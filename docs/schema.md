# Schema do banco — PluvIFFar

Fonte da verdade: `src/main/resources/db/migration/` (Flyway). Este documento explica as escolhas.

## Visão geral

```
schema app                                         schema telemetry
──────────                                         ────────────────
users ─1───N─ gateways ─1───N─ stations ◀── FK ── readings        (hypertable, 30 dias)
                 ▲   (id, owner_id)  │    ◀── FK ── device_status   (hypertable, 30 dias)
                 └──── FK composta ──┘    ◀── FK ── config_reports  (tabela comum)
spring_session ─1───N─ spring_session_attributes
flyway_schema_history
```

| Migration | Conteúdo |
|---|---|
| `V1__initial_schema.sql` | schemas `app`/`telemetry`, tabelas, hypertables, sessões, compressão condicional |
| `V2__SeedAdmin.java` | primeiro ADMIN a partir de `PLUVIFFAR_ADMIN_EMAIL` / `PLUVIFFAR_ADMIN_PASSWORD` (BCrypt gerado na migration; mín. 12 caracteres) |

## Decisões

- **`app` × `telemetry`**: a API escreve em `app` via JPA; o worker escreve em `telemetry` (e só nas colunas de config de `app.stations`/`app.gateways`). As migrations vivem **neste repo**; o worker não roda DDL.
- **PK `uuid` (`uuidv7()`) + `hardware_id` único**: a telemetria referencia o *cadastro*, não o chip. Se o equipamento for removido e recadastrado por outro produtor, o histórico antigo não vaza para o novo dono.
- **Soft delete (`deleted_at`)**: dado medido não se apaga e as FKs da telemetria são `ON DELETE RESTRICT`. `hardware_id` é único só entre os registros não excluídos (índice parcial).
- **`stations.owner_id` + FK composta `(gateway_id, owner_id) → gateways(id, owner_id) ON UPDATE CASCADE`**: a posse é checada em uma tabela só, e o banco garante que o dono da estação é o dono do gateway. Ao transferir um gateway, as estações acompanham. No JPA são dois `@ManyToOne` independentes, e o use case seta `owner = gateway.owner`.
- **Enums como `varchar` + `CHECK`** (`role`), compatível com `@Enumerated(STRING)`.
- **Email** `varchar(254)` + `CHECK (email = lower(email))` + `UNIQUE`: sem `citext` (não podemos criar extensões na VPS). A API normaliza para minúsculas.
- **`numeric` para chuva** (`precipitation_mm numeric(6,2)`, `mm_per_tip numeric(5,3)`): é somada e precisa ser exata. **`real`** para medidas analógicas (`battery_v`).
- **Estação pública precisa de localização** (`CHECK`).
- **`received_at`** nas hypertables: detecta relógio errado e dado atrasado.
- **`config_reports` não é hypertable**: `conf` é raro e sem timestamp. O estado atual fica nas colunas de `stations`/`gateways`, e o histórico só ganha linha quando algo muda.

## Hypertables

| Tabela | Tempo | Chunk | PK / dedup | Índices extras |
|---|---|---|---|---|
| `telemetry.readings` | `measured_at` | 30 dias | `(station_id, measured_at)` + `ON CONFLICT DO NOTHING` | padrão `measured_at DESC` |
| `telemetry.device_status` | `measured_at` | 30 dias | `(station_id, measured_at)` | `(gateway_id, measured_at DESC)` |

`seq` fica fora da PK (zera no reboot): serve para detectar lacunas e duplicatas. Chunk de 30 dias ≈ 300 MB com 100 estações a 1 leitura/min, bem abaixo de 25% da RAM.

Compressão: bloco `DO` no fim da V1, executado só se `timescaledb.license = 'timescale'`. Em produção e no dev (`apache`) ele é pulado com um `NOTICE`. Sem retenção.

> Dev roda Timescale 2.31 (imagem `latest-pg18`); produção roda 2.25.1. Recursos novos (> 2.25) não devem ser usados nas migrations.

## Mapeamento protocolo v1.0 → colunas

Chaves: `{gateway_id}` do tópico → `gateways.hardware_id`; `id` do payload → `stations.hardware_id` (ambos `deleted_at IS NULL`).

| Tópico | Payload → coluna |
|---|---|
| `dados` → `telemetry.readings` | `id`→`station_id` (lookup) · `tips_por_minuto`→`tips_per_minute` · `mm`→`precipitation_mm` · `timestamp`→`measured_at` (`to_timestamp`) · `seq`→`seq` |
| `status` → `telemetry.device_status` | `id`→`station_id` · tópico→`gateway_id` · `vbat`→`battery_v` · `bat`→`battery_pct` · `rssi_gt`→`rssi_gateway_dbm` · `rssi_pluv`→`rssi_station_dbm` · `uptime_gt_s`→`uptime_gateway_s` · `uptime_pluv_s`→`uptime_station_s` · `timestamp`→`measured_at` |
| `conf` → estado atual + `telemetry.config_reports` | `vers_gt`→`gateways.fw_version`/`fw_gateway` · `hw_gt`→`gateways.hw_version`/`hw_gateway` · `vers_pl`→`stations.fw_version`/`fw_station` · `hw_pl`→`stations.hw_version`/`hw_station` · `tempo_envio`→`send_interval_s` · `mm_tips`→`mm_per_tip` · `now()`→`config_reported_at`/`reported_at` |

## O que muda para o worker (a implementar depois)

1. Assinar `pluviffar/+/dados|status|conf` (largar `pluvio/dados`).
2. Fim do auto-cadastro: resolver gateway e estação por `hardware_id` (não excluídos); se desconhecido → descartar + log. Cache com TTL curto (1–5 min).
3. Descartar se `stations.gateway_id` ≠ gateway do tópico.
4. `dados`/`status` → `INSERT ... ON CONFLICT (station_id, measured_at) DO NOTHING`.
5. `conf` → numa transação: `UPDATE` das colunas de config em `app.stations`/`app.gateways` e `INSERT` em `telemetry.config_reports` se mudou algo.
6. Validar timestamp (rejeitar < 2024-01-01 ou > agora + 10 min).
7. Futuro: role `pluviffar_worker` com grants mínimos.

## Premissas a confirmar com o TCC 1

- `conf` é reportado pelo dispositivo (a API não publica por enquanto).
- `mm` = chuva no intervalo desde o último envio.
- `seq` cabe em `bigint` e zera no reboot.
- Gateway sincroniza NTP; timestamp inválido é descartado.
- Dados legados em `dados.*` na VPS: a V1 não os toca; uma eventual migração é tarefa separada.
