# Protocolo MQTT PluvIFFar v2 — proposta (PT → EN)

> **Proposta, não implementada.** Firmware e worker continuam no v1.0 (em português). A mudança será feita depois pelo Eduardo.

Os nomes v2 coincidem com as colunas do banco (`docs/schema.md`), então o worker v2 mapeia 1:1.

## Tópicos

| v1.0 | v2 |
|---|---|
| `pluviffar/{gateway_id}/dados` | `pluviffar/{gateway_id}/data` |
| `pluviffar/{gateway_id}/conf` | `pluviffar/{gateway_id}/config` |
| `pluviffar/{gateway_id}/status` | `pluviffar/{gateway_id}/status` |

Como `dados`/`conf` mudam de nome, o worker aceita v1 e v2 ao mesmo tempo (distingue pelo tópico) durante a atualização do firmware. A ACL `pluviffar/#` já cobre os dois. Sugestão: incluir `"v": 2` em todo payload.

## Campos

| Tópico | v1.0 | v2 | Tipo / unidade |
|---|---|---|---|
| todos | `id` | `station_id` | string hex |
| data | `tips_por_minuto` | `tips_per_minute` | inteiro |
| data | `mm` | `precipitation_mm` | decimal, mm |
| data, status | `timestamp` | `timestamp` | epoch s (UTC) |
| data | `seq` | `seq` | inteiro |
| config | `vers_gt` | `fw_gateway` | string |
| config | `vers_pl` | `fw_station` | string |
| config | `hw_gt` | `hw_gateway` | string |
| config | `hw_pl` | `hw_station` | string |
| config | `tempo_envio` | `send_interval_s` | inteiro, s |
| config | `mm_tips` | `mm_per_tip` | decimal, mm |
| status | `vbat` | `battery_v` | decimal, V |
| status | `bat` | `battery_pct` | inteiro, 0–100 |
| status | `rssi_gt` | `rssi_gateway_dbm` | inteiro, dBm |
| status | `rssi_pluv` | `rssi_station_dbm` | inteiro, dBm |
| status | `uptime_gt_s` | `uptime_gateway_s` | inteiro, s |
| status | `uptime_pluv_s` | `uptime_station_s` | inteiro, s |

## Exemplos v2

```json
// pluviffar/gt-570db4/data
{ "v": 2, "station_id": "580db2", "tips_per_minute": 4, "precipitation_mm": 0.8, "timestamp": 1790550000, "seq": 1 }

// pluviffar/gt-570db4/config
{ "v": 2, "station_id": "580db2", "fw_gateway": "1.0.0", "fw_station": "1.0.0", "hw_gateway": "1.0", "hw_station": "1.0",
  "send_interval_s": 60, "mm_per_tip": 0.20 }

// pluviffar/gt-570db4/status
{ "v": 2, "station_id": "580db2", "battery_v": 6.5, "battery_pct": 74, "rssi_gateway_dbm": -86, "rssi_station_dbm": -60,
  "uptime_gateway_s": 86432, "uptime_station_s": 85321, "timestamp": 1790550000 }
```
