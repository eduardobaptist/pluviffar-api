# Worker PluvIFFar — como funciona

O worker (`pluvio_worker.py`) é o processo que **tira os dados do MQTT e grava no PostgreSQL**. Ele é a única ponte entre os dispositivos e o banco: a API só lê o que o worker gravou.

```
Pluviômetro ─ESP-NOW─▶ Gateway ─MQTT publish─▶ Mosquitto (VPS, :1883)
                                                   │  (empurra as mensagens assinadas)
                                                   ▼
                                    ┌──────────── worker ────────────┐
                                    │ thread de rede (paho)          │
                                    │   on_message → fila.put_nowait │
                                    │            │                   │
                                    │        queue.Queue             │
                                    │            │                   │
                                    │ thread principal               │
                                    │   laco_gravacao → processar    │
                                    └────────────┼───────────────────┘
                                                 ▼
                                PostgreSQL (readings, device_status,
                                config_reports, stations, gateways)
```

## 1. Como ele "escuta" o MQTT (não é um servidor)

O worker **não é um servidor**: não abre porta e não recebe conexões. Quem é servidor é o **Mosquitto** (o *broker*). O worker é um **cliente MQTT**, igual aos gateways. A diferença é o papel:

- o gateway **publica** (`publish`) em `pluviffar/gt-570db4/dados`;
- o worker **assina** (`subscribe`) `pluviffar/#` (o `#` casa com qualquer coisa abaixo de `pluviffar/`);
- o broker recebe a publicação e **empurra** uma cópia para cada cliente que assinou um filtro compatível.

A conexão TCP vai **do worker até o broker** (saída para `localhost:1883` na VPS) e fica aberta. É por ela que as mensagens chegam. Como não há porta aberta pelo worker, não há nada novo para expor no firewall.

### A biblioteca: `paho-mqtt`

[`paho-mqtt`](https://pypi.org/project/paho-mqtt/) (projeto Eclipse Paho, versão 2.x) é a biblioteca cliente MQTT padrão em Python. Ela implementa o protocolo: conexão, autenticação, keepalive (PINGREQ a cada 60 s), assinatura, QoS, reconexão. O worker só registra **callbacks** (funções que a lib chama quando algo acontece):

| Callback | Quando a lib chama | O que o worker faz |
|---|---|---|
| `on_connect` | conexão aceita ou recusada | se aceita, `subscribe(MQTT_TOPIC, qos=1)`; se recusada (usuário/senha), log ERROR |
| `on_subscribe` | broker respondeu à assinatura | se recusada (ACL), log ERROR dizendo para conferir a ACL |
| `on_message` | chegou uma mensagem | só põe `(hora_de_chegada, tópico, payload)` na fila |
| `on_disconnect` | caiu a conexão | log WARNING (a lib reconecta sozinha) |

Detalhes da conexão (`criar_cliente` e `main`):

- `mqtt.Client(CallbackAPIVersion.VERSION2, client_id="pluvio-worker")`, usuário/senha do `.env` (`pluviometro`; a ACL do Mosquitto libera `readwrite pluviffar/#` para ele).
- `connect_async(...)` + `loop_start()`: o `loop_start()` cria uma **thread em segundo plano da própria paho** que cuida do socket (lê, escreve, keepalive) e chama os callbacks. Por isso `on_message` roda nessa thread, não na principal.
- `reconnect_delay_set(1, 60)`: se o broker cair, a lib tenta reconectar com espera crescente de 1 s a 60 s. Ao reconectar, `on_connect` assina de novo.
- **QoS 1** na assinatura: o broker entrega "pelo menos uma vez" e espera o PUBACK. A paho confirma assim que `on_message` retorna, ou seja, quando a mensagem já está **na fila**, não no banco (ver limitações).

### Quem mantém o processo vivo: systemd

`pluvio-worker.service` transforma o script num serviço do sistema:

- `ExecStart=/opt/pluvio-worker/venv/bin/python -u /opt/pluvio-worker/pluvio_worker.py`: roda no venv próprio, `-u` para o log sair sem buffer no journal.
- `EnvironmentFile=/opt/pluvio-worker/.env`: as variáveis (`MQTT_*`, `PG_DSN`...) vêm daqui.
- `User=pluvio`: usuário de sistema sem shell e sem privilégios.
- `Restart=always`, `RestartSec=5`: se o processo morrer (erro, OOM), volta em 5 s. `WantedBy=multi-user.target`: sobe no boot.
- `After=... mosquitto.service postgresql.service`: sobe depois do broker e do banco.
- `TimeoutStopSec=30`: no `systemctl stop`, o worker tem 30 s para esvaziar a fila antes de levar SIGKILL.
- Endurecimento: `NoNewPrivileges`, `ProtectSystem=strict` (disco somente leitura), `ProtectHome`, `PrivateTmp`. O worker não precisa escrever em disco: loga no stdout, que vai para o journal.

## 2. A fila (por que existem duas threads)

```python
fila = queue.Queue(maxsize=QUEUE_MAX)   # padrão 10000
```

`queue.Queue` é a fila **thread-safe** da biblioteca padrão do Python, em **memória**. Ela separa duas tarefas com velocidades diferentes:

- **Produtor: thread da paho (`on_message`).** Só faz `fila.put_nowait(...)`, que é instantâneo. Assim a thread de rede nunca trava esperando o banco e continua respondendo keepalive e recebendo mensagens. Se a fila estiver **cheia**, a mensagem é descartada com log ERROR (`fila cheia`).
- **Consumidor: thread principal (`laco_gravacao`).** Tira uma mensagem por vez (`fila.get(timeout=1)`), grava, pega a próxima.

O laço de gravação, passo a passo:

1. Se não há mensagem `pendente`, pega uma da fila. O timeout de 1 s serve para conferir de tempos em tempos se recebeu pedido para parar.
2. Se não há conexão com o banco, conecta (`psycopg.connect(PG_DSN, autocommit=True)`).
3. Chama `processar(...)`. Deu certo ou a mensagem foi descartada por ser inválida → `pendente = None` e segue.
4. **Banco fora do ar** (`OperationalError`): fecha a conexão, loga `PostgreSQL indisponível ... N na fila`, espera 5 s e tenta **a mesma mensagem** de novo (a `pendente` não é perdida). Enquanto isso, a thread da paho continua enchendo a fila. Quando o banco volta, a fila é gravada em ordem.
5. Erro inesperado (bug): log com stack trace, descarta só aquela mensagem e continua. Um payload estranho não derruba o worker.

Capacidade: com 10 000 posições e, por exemplo, 20 estações mandando `dados` + `status` por minuto (~40 msg/min), a fila aguenta cerca de 4 h de banco fora do ar.

### Encerramento limpo

`SIGTERM` (`systemctl stop/restart`) ou `SIGINT` (Ctrl+C) chamam `encerrar`, que só liga o evento `parar`. O laço **continua até a fila esvaziar** (`parar.is_set() and fila.empty()`), e então `main` desconecta do broker e para a thread da paho. Log final: `worker encerrado`.

## 3. Fluxo de cada mensagem (`processar`)

### 3.1 Validação antes do banco
1. **Tópico** (`ler_topico`): precisa ser `pluviffar/{gateway}/{tipo}`, com 3 níveis e `tipo` ∈ `dados|status|conf`. O gateway vai para minúsculas (como o `hardware_id` da API).
2. **JSON**: precisa ser um objeto válido.
3. **`id`** (chip do pluviômetro) obrigatório, convertido para minúsculas.
4. **Montagem do registro** (`montar_registro`): traduz os campos pt-BR do protocolo v1.0 para os nomes das colunas. Os valores vão **como vieram**: quem valida faixa e tipo são as constraints do banco.
5. **Timestamp** (`ler_timestamp`, para `dados`/`status`): aceita epoch em segundos ou milissegundos (número ou texto) e ISO 8601 (sem fuso → `TZ_SEM_OFFSET`). Se não vier ou for ilegível, usa a **hora de chegada** no worker.

Falhou algum passo → `WARNING descartada [tópico] motivo: payload` (até 300 caracteres) e fim.

### 3.2 Cadastro (`conferir_cadastro`), dentro da transação
Um SELECT resolve os chips para os IDs (uuid) do cadastro, considerando só registros **não excluídos** (`deleted_at IS NULL`):

- gateway do tópico → `gateways.id`. Não existe → descarta (`gateway 'gt-x' não cadastrado`);
- `id` do payload → `stations.id`. Não existe → descarta (`estação 'x' não cadastrada`);
- a estação precisa estar **no gateway do tópico**; senão descarta (`estação ... cadastrada no gateway A, não em B`).

Não há auto-cadastro: equipamento só gera dado depois de cadastrado pela API.

### 3.3 Gravação por tipo

| Tipo | Destino | Mapeamento (payload → coluna) | Duplicado |
|---|---|---|---|
| `dados` | `readings` | `tips_por_minuto`→`tips_per_minute`, `mm`→`precipitation_mm`, `timestamp`→`measured_at`, `seq`→`seq` | `ON CONFLICT (station_id, measured_at) DO NOTHING` → log `repetida, ignorada` |
| `status` | `device_status` | `vbat`→`battery_v`, `bat`→`battery_pct`, `rssi_gt`→`rssi_gateway_dbm`, `rssi_pluv`→`rssi_station_dbm`, `uptime_gt_s`→`uptime_gateway_s`, `uptime_pluv_s`→`uptime_station_s`, `timestamp`→`measured_at`, gateway do tópico→`gateway_id` | idem |
| `conf` | `config_reports` + `stations`/`gateways` | `vers_gt`→`fw_gateway`/`gateways.fw_version`, `hw_gt`→`hw_gateway`/`gateways.hw_version`, `vers_pl`→`fw_station`/`stations.fw_version`, `hw_pl`→`hw_station`/`stations.hw_version`, `tempo_envio`→`send_interval_s`, `mm_tips`→`mm_per_tip`, hora de chegada→`reported_at`/`config_reported_at` | histórico só se mudou |

Como funciona o `conf` (o `conf` não tem timestamp e se repete a cada envio):

1. `INSERT INTO config_reports ... WHERE NOT EXISTS (estado atual igual)`: compara com as colunas atuais de `stations`/`gateways` usando `IS NOT DISTINCT FROM` (trata NULL como valor). Só entra no histórico o que **mudou** (ex.: atualização de firmware 1.1.0 → 1.2.0).
2. `UPDATE stations` e `UPDATE gateways` com os valores recebidos, **sempre**, para `config_reported_at` dizer quando chegou o último conf.

Tudo isso numa **única transação**: ou grava tudo, ou nada.

### 3.4 Banco recusou
`IntegrityError` / `DataError` / `ProgrammingError` (campo obrigatório faltando, `tips_por_minuto` negativo, `bat` > 100, tipo errado...) → a transação é desfeita e sai `WARNING descartada [...] banco recusou (motivo): payload`. Exemplo real do teste:
`banco recusou (new row ... violates check constraint "readings_tips_ck")`.

### 3.5 Monitoramento do `seq`
Depois de gravar um `dados`, `conferir_seq` compara com o último `seq` daquela estação (guardado em memória):
- `seq` = anterior + 1 → normal;
- pulou (ex.: 1 → 4) → `WARNING ... 2 mensagem(ns) de dados perdida(s)`;
- voltou (ex.: 500 → 1) → `INFO ... reinício do dispositivo?`.

É só log. A memória zera quando o worker reinicia (a primeira mensagem de cada estação não é comparada).

## 4. Configuração (`.env`)

| Variável | Padrão | Para quê |
|---|---|---|
| `MQTT_HOST` / `MQTT_PORT` | `localhost` / `1883` | broker |
| `MQTT_USER` / `MQTT_PASS` | `pluviometro` / **obrigatória** | credenciais do Mosquitto |
| `MQTT_TOPIC` | `pluviffar/#` | filtro assinado |
| `MQTT_CLIENT_ID` | `pluvio-worker` | ID no broker (dois clientes com o mesmo ID se derrubam) |
| `PG_DSN` | **obrigatória** | conexão libpq, ex.: `host=localhost dbname=pluviffar user=pluviffar password=...` |
| `QUEUE_MAX` | `10000` | tamanho da fila em memória |
| `TZ_SEM_OFFSET` | `America/Sao_Paulo` | fuso de timestamps ISO sem offset |
| `LOG_LEVEL` | `INFO` | `DEBUG` mostra também "conf sem mudança" |

## 5. Instalação e operação na VPS

```bash
cd worker && sudo bash instalar.sh   # instala/atualiza; preserva o .env existente
sudo nano /opt/pluvio-worker/.env    # primeira vez: senhas
sudo systemctl restart pluvio-worker
journalctl -u pluvio-worker -f       # acompanhar
```

O `instalar.sh` instala `python3-venv`, cria o usuário `pluvio`, copia `pluvio_worker.py` e `requirements.txt` para `/opt/pluvio-worker`, cria o venv e instala `paho-mqtt` e `psycopg[binary]`. Também copia o `.env.example` (só se ainda não houver `.env`), corrige `MQTT_TOPIC` antigo (`pluvio/...`), instala e habilita o service. Se ainda houver senha placeholder no `.env`, ele avisa em vez de iniciar.

Teste manual (com o Mosquitto da VPS):
```bash
mosquitto_pub -u pluviometro -P '<senha>' -q 1 -t pluviffar/gt-570db4/dados \
  -m '{"id":"580db2","tips_por_minuto":4,"mm":0.8,"timestamp":'"$(date +%s)"',"seq":1}'
```

## 6. Limitações conhecidas (e como resolver)

1. **Mensagens publicadas com o worker fora do ar se perdem.** A sessão MQTT não é persistente (`clean_session` padrão): durante um restart, o broker não guarda nada para o worker. Solução: `clean_start=False` + `session_expiry_interval` (MQTT 5) ou `clean_session=False` (3.1.1), com `client_id` fixo. Também exige que o gateway publique com QoS 1.
2. **A fila é só memória.** A paho confirma (PUBACK) ao broker quando a mensagem entra na fila, não quando é gravada. Se o processo morrer com mensagens na fila (crash, SIGKILL), elas somem. Um `stop` normal esvazia a fila antes. Solução, se necessário: ack manual depois do commit.
3. **Timestamp fora de faixa não é rejeitado** (relógio do gateway sem NTP grava data errada). O `docs/schema.md` sugere rejeitar < 2024-01-01 ou > agora + 10 min.
4. **Uma consulta de cadastro por mensagem.** Barato no volume atual; dá para pôr cache com TTL curto se crescer.
5. **MQTT sem TLS** (porta 1883): credenciais e dados trafegam em texto puro entre o gateway e a VPS.
6. Usa o mesmo usuário de banco da API (`pluviffar`). Ideal: role `pluviffar_worker` com permissão só nas tabelas que escreve.
