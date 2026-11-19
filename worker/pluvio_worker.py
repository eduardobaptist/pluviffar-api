#!/usr/bin/env python3
"""Worker PluvIFFar — Protocolo MQTT PluvIFFAR v1.0.

Assina pluviffar/# e grava cada tipo de mensagem na sua tabela (schema public):

    pluviffar/{gateway_id}/dados   -> readings
    pluviffar/{gateway_id}/status  -> device_status
    pluviffar/{gateway_id}/conf    -> estado atual em stations/gateways
                                      + config_reports (só quando muda)

Gateway e estação precisam estar cadastrados (pela API, pelo hardware_id) e a
estação precisa pertencer ao gateway do tópico; senão a mensagem é descartada
com WARNING.

Validação no worker: tópico com 3 níveis e tipo conhecido, JSON com "id", gateway
e estação cadastrados (estação no gateway do tópico).
Sem "timestamp" (ou ilegível) em dados/status, usa a hora de chegada. O nome do
gateway pode ser qualquer um. O resto (campos obrigatórios, faixas de
valores) fica a cargo das constraints do banco; o que o banco recusar é
descartado com WARNING no log, mostrando o tópico, o motivo e o payload.

A thread do MQTT só enfileira; a thread principal grava. Se o banco cair, as
mensagens esperam na fila (até QUEUE_MAX) e são gravadas quando ele volta.

Configuração por variáveis de ambiente (ver .env.example).
"""
import json
import logging
import os
import queue
import signal
import sys
import threading
import time
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

import paho.mqtt.client as mqtt
import psycopg

log = logging.getLogger("pluvio-worker")


def env(nome, padrao=None, obrigatorio=False):
    valor = os.environ.get(nome, padrao)
    if obrigatorio and not valor:
        sys.exit(f"variável de ambiente obrigatória ausente: {nome}")
    return valor


MQTT_HOST = env("MQTT_HOST", "localhost")
MQTT_PORT = int(env("MQTT_PORT", "1883"))
MQTT_USER = env("MQTT_USER", "pluviometro")
MQTT_PASS = env("MQTT_PASS", obrigatorio=True)
MQTT_TOPIC = env("MQTT_TOPIC", "pluviffar/#")
MQTT_CLIENT_ID = env("MQTT_CLIENT_ID", "pluvio-worker")
PG_DSN = env("PG_DSN", obrigatorio=True)
QUEUE_MAX = int(env("QUEUE_MAX", "10000"))
TZ_SEM_OFFSET = ZoneInfo(env("TZ_SEM_OFFSET", "America/Sao_Paulo"))
TIPOS = ("dados", "status", "conf")

fila = queue.Queue(maxsize=QUEUE_MAX)
parar = threading.Event()
ultimo_seq = {}   # hardware_id da estação -> último seq gravado (para avisar perdas)


class Invalida(ValueError):
    """Mensagem que não pode ser gravada; o texto é o motivo."""


# ---------------------------------------------------------------- MQTT

def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code.is_failure:
        log.error("broker recusou a conexão: %s (usuário/senha?)", reason_code)
        return
    log.info("conectado em %s:%s como %s, assinando %s", MQTT_HOST, MQTT_PORT, MQTT_USER, MQTT_TOPIC)
    client.subscribe(MQTT_TOPIC, qos=1)


def on_subscribe(client, userdata, mid, reason_codes, properties):
    for rc in reason_codes:
        if rc.is_failure:
            log.error("assinatura de %s recusada (%s): confira a ACL do usuário %s",
                      MQTT_TOPIC, rc, MQTT_USER)


def on_disconnect(client, userdata, flags, reason_code, properties):
    if not parar.is_set():
        log.warning("desconectado do broker (%s); reconectando", reason_code)


def on_message(client, userdata, msg):
    try:
        fila.put_nowait((datetime.now(timezone.utc), msg.topic, msg.payload))
    except queue.Full:
        log.error("fila cheia (%d), descartando %s: %r", QUEUE_MAX, msg.topic, msg.payload[:200])


def criar_cliente():
    c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=MQTT_CLIENT_ID)
    c.username_pw_set(MQTT_USER, MQTT_PASS)
    c.on_connect = on_connect
    c.on_subscribe = on_subscribe
    c.on_disconnect = on_disconnect
    c.on_message = on_message
    c.reconnect_delay_set(min_delay=1, max_delay=60)
    return c


# ---------------------------------------------------------------- leitura da mensagem

def ler_topico(topico):
    """'pluviffar/gt-570db4/dados' -> ('gt-570db4', 'dados')."""
    partes = topico.split("/")
    if len(partes) != 3 or partes[0] != "pluviffar" or not partes[1]:
        raise Invalida("tópico fora do padrão pluviffar/{gateway_id}/{tipo}")
    if partes[2] not in TIPOS:
        raise Invalida(f"tipo desconhecido {partes[2]!r} (esperado {', '.join(TIPOS)})")
    return partes[1].lower(), partes[2]   # hardware_id na API é sempre minúsculo


def ler_timestamp(d, recebido_em, topico):
    """Epoch em s ou ms (número ou texto) ou ISO 8601 -> datetime com fuso.
    Sem timestamp (ou ilegível): usa a hora de chegada no worker."""
    valor = d.get("timestamp")
    if valor is None:
        log.info("[%s] sem 'timestamp': usando a hora de chegada", topico)
        return recebido_em
    try:
        if isinstance(valor, str) and valor.strip().isdigit():
            valor = int(valor.strip())
        if isinstance(valor, (int, float)) and not isinstance(valor, bool):
            return datetime.fromtimestamp(valor / 1000 if valor > 1e11 else valor, tz=timezone.utc)
        if isinstance(valor, str):
            dt = datetime.fromisoformat(valor.strip().replace("Z", "+00:00"))
            return dt if dt.tzinfo else dt.replace(tzinfo=TZ_SEM_OFFSET)
    except (ValueError, OverflowError, OSError):
        pass
    log.info("[%s] 'timestamp' não reconhecido (%r): usando a hora de chegada", topico, valor)
    return recebido_em


def montar_registro(tipo, d, recebido_em, topico):
    """Payload (campos em pt-BR, protocolo v1.0) -> colunas da tabela (inglês).
    Os valores vão como vieram; o banco confere."""
    if d.get("id") in (None, ""):
        raise Invalida("campo 'id' ausente")
    r = {"station_hw": str(d["id"]).lower()}
    if tipo == "dados":
        r.update(measured_at=ler_timestamp(d, recebido_em, topico),
                 tips_per_minute=d.get("tips_por_minuto"),
                 precipitation_mm=d.get("mm"),
                 seq=d.get("seq"))
    elif tipo == "status":
        r.update(measured_at=ler_timestamp(d, recebido_em, topico),
                 battery_v=d.get("vbat"),
                 battery_pct=d.get("bat"),
                 rssi_gateway_dbm=d.get("rssi_gt"),
                 rssi_station_dbm=d.get("rssi_pluv"),
                 uptime_gateway_s=d.get("uptime_gt_s"),
                 uptime_station_s=d.get("uptime_pluv_s"))
    else:  # conf: não tem timestamp, usa a hora de chegada
        r.update(reported_at=recebido_em,
                 fw_gateway=d.get("vers_gt"),
                 fw_station=d.get("vers_pl"),
                 hw_gateway=d.get("hw_gt"),
                 hw_station=d.get("hw_pl"),
                 send_interval_s=d.get("tempo_envio"),
                 mm_per_tip=d.get("mm_tips"))
    return r


# ---------------------------------------------------------------- banco

# Resolve os chips (hardware_id) para os IDs do cadastro, só entre os não excluídos:
# (id do gateway do tópico, id da estação, hardware_id do gateway em que a estação está)
SQL_CADASTRO = """
SELECT (SELECT id FROM gateways
         WHERE hardware_id = %(gateway_hw)s AND deleted_at IS NULL),
       s.id,
       g.hardware_id
FROM (SELECT 1) AS um
LEFT JOIN stations s ON s.hardware_id = %(station_hw)s AND s.deleted_at IS NULL
LEFT JOIN gateways g ON g.id = s.gateway_id
"""

SQL_INSERIR = {
    "dados": """
        INSERT INTO readings
            (station_id, measured_at, tips_per_minute, precipitation_mm, seq)
        VALUES (%(station_id)s, %(measured_at)s, %(tips_per_minute)s,
                %(precipitation_mm)s, %(seq)s)
        ON CONFLICT (station_id, measured_at) DO NOTHING
    """,
    "status": """
        INSERT INTO device_status
            (station_id, gateway_id, measured_at, battery_v, battery_pct, rssi_gateway_dbm,
             rssi_station_dbm, uptime_gateway_s, uptime_station_s)
        VALUES (%(station_id)s, %(gateway_id)s, %(measured_at)s, %(battery_v)s, %(battery_pct)s,
                %(rssi_gateway_dbm)s, %(rssi_station_dbm)s, %(uptime_gateway_s)s,
                %(uptime_station_s)s)
        ON CONFLICT (station_id, measured_at) DO NOTHING
    """,
    # histórico: só grava se algo mudou em relação ao estado atual em stations/gateways
    "conf": """
        INSERT INTO config_reports
            (station_id, gateway_id, reported_at, fw_gateway, hw_gateway, fw_station,
             hw_station, send_interval_s, mm_per_tip)
        SELECT %(station_id)s, %(gateway_id)s, %(reported_at)s, %(fw_gateway)s::text,
               %(hw_gateway)s::text, %(fw_station)s::text, %(hw_station)s::text,
               %(send_interval_s)s::integer, %(mm_per_tip)s::numeric
        WHERE NOT EXISTS (
            SELECT 1 FROM stations s JOIN gateways g ON g.id = s.gateway_id
            WHERE s.id = %(station_id)s
              AND g.fw_version      IS NOT DISTINCT FROM %(fw_gateway)s::text
              AND g.hw_version      IS NOT DISTINCT FROM %(hw_gateway)s::text
              AND s.fw_version      IS NOT DISTINCT FROM %(fw_station)s::text
              AND s.hw_version      IS NOT DISTINCT FROM %(hw_station)s::text
              AND s.send_interval_s IS NOT DISTINCT FROM %(send_interval_s)s::integer
              AND s.mm_per_tip      IS NOT DISTINCT FROM %(mm_per_tip)s::numeric)
    """,
}

# conf: estado atual (sempre atualiza, para config_reported_at dizer quando chegou o último)
SQL_CONF_ATUAL = (
    """
    UPDATE stations
    SET fw_version = %(fw_station)s, hw_version = %(hw_station)s,
        send_interval_s = %(send_interval_s)s, mm_per_tip = %(mm_per_tip)s,
        config_reported_at = %(reported_at)s
    WHERE id = %(station_id)s
    """,
    """
    UPDATE gateways
    SET fw_version = %(fw_gateway)s, hw_version = %(hw_gateway)s,
        config_reported_at = %(reported_at)s
    WHERE id = %(gateway_id)s
    """,
)


def conferir_seq(estacao, seq):
    if not isinstance(seq, int) or isinstance(seq, bool):
        return
    anterior = ultimo_seq.get(estacao)
    ultimo_seq[estacao] = seq
    if anterior is None or seq == anterior + 1:
        return
    if seq > anterior + 1:
        log.warning("estação %s: seq pulou de %d para %d (%d mensagem(ns) de dados perdida(s))",
                    estacao, anterior, seq, seq - anterior - 1)
    else:
        log.info("estação %s: seq voltou de %d para %d (reinício do dispositivo?)", estacao, anterior, seq)


def conferir_cadastro(conn, gateway_hw, station_hw):
    """Devolve (gateway_id, station_id) do cadastro ou levanta Invalida."""
    gateway_id, station_id, gateway_da_estacao = conn.execute(
        SQL_CADASTRO, {"gateway_hw": gateway_hw, "station_hw": station_hw}).fetchone()
    if gateway_id is None:
        raise Invalida(f"gateway {gateway_hw!r} não cadastrado")
    if station_id is None:
        raise Invalida(f"estação {station_hw!r} não cadastrada")
    if gateway_da_estacao != gateway_hw:
        raise Invalida(f"estação {station_hw!r} cadastrada no gateway {gateway_da_estacao!r}, não em {gateway_hw!r}")
    return gateway_id, station_id


def processar(conn, recebido_em, topico, payload):
    texto = payload.decode("utf-8", errors="replace")
    try:
        gateway, tipo = ler_topico(topico)
        try:
            d = json.loads(texto)
        except json.JSONDecodeError as e:
            raise Invalida(f"JSON inválido: {e}") from None
        if not isinstance(d, dict):
            raise Invalida("payload não é um objeto JSON")
        registro = montar_registro(tipo, d, recebido_em, topico)
    except Invalida as e:
        log.warning("descartada [%s] %s: %s", topico, e, texto[:300])
        return

    estacao = registro["station_hw"]
    try:
        with conn.transaction():
            registro["gateway_id"], registro["station_id"] = conferir_cadastro(conn, gateway, estacao)
            gravou = conn.execute(SQL_INSERIR[tipo], registro).rowcount
            if tipo == "conf":
                for sql in SQL_CONF_ATUAL:
                    conn.execute(sql, registro)
    except Invalida as e:
        log.warning("descartada [%s] %s: %s", topico, e, texto[:300])
        return
    except (psycopg.errors.IntegrityError, psycopg.errors.DataError, psycopg.ProgrammingError) as e:
        # o banco recusou: campo obrigatório faltando, valor fora da faixa, tipo errado...
        motivo = str(e).strip().splitlines()[0]
        log.warning("descartada [%s] banco recusou (%s): %s", topico, motivo, texto[:300])
        return

    if tipo == "conf":
        if gravou:
            log.info("[%s] configuração nova de %s", topico, estacao)
        else:
            log.debug("[%s] configuração de %s sem mudança", topico, estacao)
    elif gravou:
        log.info("[%s] %s %s", topico, estacao, registro["measured_at"].isoformat())
        if tipo == "dados":
            conferir_seq(estacao, registro["seq"])
    else:
        log.info("[%s] repetida, ignorada: %s %s", topico, estacao, registro["measured_at"].isoformat())


def laco_gravacao():
    conn, pendente = None, None
    while True:
        if pendente is None:
            if parar.is_set() and fila.empty():
                break
            try:
                pendente = fila.get(timeout=1)
            except queue.Empty:
                continue
        try:
            if conn is None or conn.closed:
                conn = psycopg.connect(PG_DSN, autocommit=True, connect_timeout=10)
                log.info("conectado ao PostgreSQL")
            processar(conn, *pendente)
            pendente = None
        except psycopg.OperationalError as e:
            log.warning("PostgreSQL indisponível (%s); nova tentativa em 5 s, %d na fila",
                        str(e).strip(), fila.qsize() + 1)
            if conn is not None:
                conn.close()
            conn = None
            time.sleep(5)
        except Exception:
            log.exception("erro inesperado; mensagem descartada: %r", pendente)
            pendente = None
    if conn is not None:
        conn.close()


# ---------------------------------------------------------------- main

def main():
    logging.basicConfig(level=env("LOG_LEVEL", "INFO").upper(),
                        format="%(asctime)s %(levelname)s %(message)s")

    def encerrar(signum, frame):
        log.info("sinal %s recebido, encerrando", signum)
        parar.set()

    signal.signal(signal.SIGTERM, encerrar)
    signal.signal(signal.SIGINT, encerrar)

    cliente = criar_cliente()
    cliente.connect_async(MQTT_HOST, MQTT_PORT, keepalive=60)
    cliente.loop_start()
    try:
        laco_gravacao()   # só retorna depois de parar e esvaziar a fila
    finally:
        cliente.disconnect()
        cliente.loop_stop()
        log.info("worker encerrado")


if __name__ == "__main__":
    main()
