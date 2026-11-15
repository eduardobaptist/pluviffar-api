-- =====================================================================
-- PluvIFFar — dados FALSOS para testar a API (somente dev/homologação!)
--
-- NÃO é migration nem seed: rode à mão, quando quiser, no banco de dev:
--   docker compose exec -T db psql -U pluviffar -d pluviffar -v ON_ERROR_STOP=1 < docs/dev/fake-data.sql
--
-- Cria:
--   * 1 produtor   produtor.teste@pluviffar.com.br / senha: Produtor@2026 (sem troca obrigatória)
--   * 2 gateways   gt-a1b2c3 (Sede) e gt-d4e5f6 (Lavoura Norte)
--   * 4 estações   2 públicas, 1 privada com localização, 1 privada sem localização
--                  ("Sede - Pomar" é a favorita do produtor)
--   * leituras a cada 15 min de 01/01/2025 até agora (zeros incluídos; chuva só em "eventos")
--   * status a cada 1 h, histórico de conf (fw 1.0.0 → 1.1.0 em 15/06/2026)
--   * falhas de comunicação propositais (buracos nas séries e saltos de seq)
--
-- É reexecutável: antes de inserir, apaga tudo que pertence ao produtor de teste.
-- Os valores são aleatórios (setseed ajuda, mas o plano do Postgres pode mudar a ordem): totais variam entre execuções.
-- Tudo fica no schema public (mesmo da API).
-- IDs fixos para facilitar testes (curl/Swagger):
--   usuário  0193f000-0000-7000-8000-000000000001
--   gateways 0193f000-0000-7000-8000-000000000101 / ...0102
--   estações 0193f000-0000-7000-8000-000000000201 / ...0202 / ...0203 / ...0204
-- =====================================================================

BEGIN;

SELECT setseed(0.2026);

-- ---------------------------------------------------------------------
-- 0) limpeza de execuções anteriores (FKs são RESTRICT: telemetria primeiro)
-- ---------------------------------------------------------------------
CREATE TEMP TABLE fake_owner ON COMMIT DROP AS
SELECT id FROM users
WHERE email = 'produtor.teste@pluviffar.com.br'
   OR id = '0193f000-0000-7000-8000-000000000001';

DELETE FROM readings       WHERE station_id IN (SELECT id FROM stations WHERE owner_id IN (SELECT id FROM fake_owner));
DELETE FROM device_status  WHERE station_id IN (SELECT id FROM stations WHERE owner_id IN (SELECT id FROM fake_owner));
DELETE FROM config_reports WHERE station_id IN (SELECT id FROM stations WHERE owner_id IN (SELECT id FROM fake_owner));
DELETE FROM stations             WHERE owner_id IN (SELECT id FROM fake_owner);
DELETE FROM gateways             WHERE owner_id IN (SELECT id FROM fake_owner);
DELETE FROM spring_session       WHERE principal_name IN (SELECT id::text FROM fake_owner);
DELETE FROM users                WHERE id IN (SELECT id FROM fake_owner);

-- ---------------------------------------------------------------------
-- 1) produtor (BCrypt de "Produtor@2026")
-- ---------------------------------------------------------------------
INSERT INTO users (id, name, email, password_hash, role, active, must_change_password, created_at, updated_at)
VALUES ('0193f000-0000-7000-8000-000000000001',
        'João da Silva (teste)',
        'produtor.teste@pluviffar.com.br',
        '$2a$10$gf00Gtl.xJjiF2mjy17NbO2zV62syLF/36nsd0q1loQ8mo1j1p682',
        'PRODUCER', true, false,
        '2024-12-15 10:00:00-03', now());

-- ---------------------------------------------------------------------
-- 2) gateways (estado atual do conf: fw 1.1.0 desde 15/06/2026)
-- ---------------------------------------------------------------------
INSERT INTO gateways (id, hardware_id, owner_id, name, description, fw_version, hw_version,
                          config_reported_at, created_at, updated_at)
VALUES ('0193f000-0000-7000-8000-000000000101', 'gt-a1b2c3', '0193f000-0000-7000-8000-000000000001',
        'Gateway Sede', 'Instalado no galpão da sede, Wi-Fi da casa', '1.1.0', '1.0',
        '2026-06-15 09:00:00-03', '2024-12-20 14:00:00-03', now()),
       ('0193f000-0000-7000-8000-000000000102', 'gt-d4e5f6', '0193f000-0000-7000-8000-000000000001',
        'Gateway Lavoura Norte', 'Na casa do caseiro, perto da lavoura', '1.1.0', '1.0',
        '2026-06-15 09:05:00-03', '2024-12-20 15:00:00-03', now());

-- ---------------------------------------------------------------------
-- 3) estações (parâmetros da simulação ficam numa tabela temporária)
-- região: interior do RS (próximo a Santo Augusto)
-- ---------------------------------------------------------------------
CREATE TEMP TABLE fake_station ON COMMIT DROP AS
SELECT * FROM (VALUES
    -- id                                     hw        gateway                                 nome                     descrição                         lat       lon       pública  mm/tip  início dos dados             fim (null = agora)  rssi_gt rssi_pl
    ('0193f000-0000-7000-8000-000000000201'::uuid, '5a10b1', '0193f000-0000-7000-8000-000000000101'::uuid, 'Sede - Pomar',          'Entre as fileiras de pêssego',  -27.8512, -53.7841, true,  0.200, '2025-01-01 00:00:00-03'::timestamptz, -72, -58),
    ('0193f000-0000-7000-8000-000000000202'::uuid, '5a10b2', '0193f000-0000-7000-8000-000000000101'::uuid, 'Sede - Horta',          'Ao lado da estufa',             -27.8530, -53.7869, false, 0.200, '2025-01-01 00:00:00-03'::timestamptz, -80, -62),
    ('0193f000-0000-7000-8000-000000000203'::uuid, '5a20c1', '0193f000-0000-7000-8000-000000000102'::uuid, 'Lavoura Norte - Soja',  'Talhão 3, divisa com a estrada', -27.8125, -53.7702, true,  0.200, '2025-01-01 00:00:00-03'::timestamptz, -86, -66),
    ('0193f000-0000-7000-8000-000000000204'::uuid, '5a20c2', '0193f000-0000-7000-8000-000000000102'::uuid, 'Lavoura Norte - Milho', null,                            null,     null,     false, 0.250, '2026-03-01 00:00:00-03'::timestamptz, -84, -70)
) AS t(id, hardware_id, gateway_id, name, description, latitude, longitude, is_public, mm_per_tip, data_start, rssi_gt, rssi_pl);

INSERT INTO stations (id, hardware_id, gateway_id, owner_id, name, description, latitude, longitude, is_public,
                          fw_version, hw_version, send_interval_s, mm_per_tip, config_reported_at, created_at, updated_at)
SELECT id, hardware_id, gateway_id, '0193f000-0000-7000-8000-000000000001', name, description, latitude, longitude, is_public,
       '1.1.0', '1.0', 900, mm_per_tip, '2026-06-15 09:00:00-03', data_start - interval '2 days', now()
FROM fake_station;

-- estação favorita do produtor (aparece ao entrar na plataforma)
UPDATE users SET favorite_station_id = '0193f000-0000-7000-8000-000000000201'
WHERE id = '0193f000-0000-7000-8000-000000000001';

-- falhas de comunicação: sem leitura nem status nesses intervalos
CREATE TEMP TABLE fake_gap ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('0193f000-0000-7000-8000-000000000202'::uuid, '2025-07-10 06:00:00-03'::timestamptz, '2025-07-15 11:00:00-03'::timestamptz), -- gateway sem Wi-Fi
    ('0193f000-0000-7000-8000-000000000203'::uuid, '2026-02-03 12:00:00-03'::timestamptz, '2026-02-04 08:30:00-03'::timestamptz), -- queda de energia
    ('0193f000-0000-7000-8000-000000000201'::uuid, '2026-09-20 00:00:00-03'::timestamptz, '2026-09-21 00:00:00-03'::timestamptz)  -- pluviômetro sem bateria
) AS t(station_id, gap_from, gap_to);

-- ---------------------------------------------------------------------
-- 4) clima diário regional: dia de chuva, total, hora de início e duração
-- (~1.700 mm/ano, mais chuva na primavera e no outono)
-- ---------------------------------------------------------------------
CREATE TEMP TABLE fake_region_day ON COMMIT DROP AS
SELECT day,
       random() < 0.27 + 0.06 * cos(2 * pi() * (extract(month FROM day) - 10) / 12)
                       + 0.03 * cos(4 * pi() * (extract(month FROM day) - 4) / 12) AS rainy,
       -- total do dia ~ exponencial (média ~17 mm), com chance de temporal
       least(-ln(1 - random()) * 17 * CASE WHEN random() < 0.05 THEN 3 ELSE 1 END, 160) AS total_mm,
       floor(random() * 22)::int   AS start_hour,
       1 + floor(random() * 9)::int AS duration_h
FROM generate_series(date '2025-01-01', current_date, interval '1 day') AS day;

-- variação local por estação (chuva nunca é igual em todo lugar)
CREATE TEMP TABLE fake_station_day ON COMMIT DROP AS
SELECT s.id AS station_id,
       r.day::date AS day,
       CASE
           WHEN r.rainy AND random() >= 0.08 THEN r.total_mm * (0.70 + random() * 0.60) -- 8%: a pancada não pegou aqui
           WHEN NOT r.rainy AND random() < 0.03 THEN 2 + random() * 8                    -- 3%: chuva isolada
           ELSE 0
       END AS total_mm,
       r.start_hour + floor(random() * 2)::int AS start_hour,
       r.duration_h
FROM fake_station s
CROSS JOIN fake_region_day r;

UPDATE fake_station_day SET duration_h = least(duration_h, 24 - start_hour);

-- ---------------------------------------------------------------------
-- 5) leituras (tópico "dados"): uma a cada 15 min, mm no intervalo
-- perfil triangular dentro do evento; arredondamento estocástico em basculadas
-- seq zera todo dia 1º (reboot mensal simulado) e "pula" nas falhas
-- ---------------------------------------------------------------------
INSERT INTO readings (station_id, measured_at, received_at, tips_per_minute, precipitation_mm, seq)
SELECT x.station_id,
       x.ts,
       x.ts + make_interval(secs => 1 + floor(random() * 4)),
       round(x.tips / 15.0)::int,
       x.tips * x.mm_per_tip,
       floor(extract(epoch FROM x.ts - date_trunc('month', x.ts, 'America/Sao_Paulo')) / 900)::bigint + 1
FROM (
    SELECT s.id AS station_id, s.mm_per_tip, t.ts,
           CASE WHEN d.total_mm > 0 AND p.pos >= 0 AND p.pos < 1 THEN
               floor(d.total_mm * (1 - 0.8 * abs(2 * p.pos - 1)) / (d.duration_h * 4 * 0.6)
                     * (0.5 + random()) / s.mm_per_tip + random())::int
           ELSE 0 END AS tips
    FROM fake_station s
    CROSS JOIN LATERAL generate_series(s.data_start + interval '15 min',
                                       date_bin('15 min', now(), timestamptz '2025-01-01 00:00:00-03'),
                                       interval '15 min') AS t(ts)
    JOIN fake_station_day d
      ON d.station_id = s.id
     AND d.day = (t.ts AT TIME ZONE 'America/Sao_Paulo')::date
    CROSS JOIN LATERAL (
        SELECT (extract(hour FROM t.ts AT TIME ZONE 'America/Sao_Paulo')
                + extract(minute FROM t.ts AT TIME ZONE 'America/Sao_Paulo') / 60.0
                - d.start_hour) / d.duration_h AS pos
    ) p
    WHERE NOT EXISTS (SELECT 1 FROM fake_gap g
                      WHERE g.station_id = s.id AND t.ts >= g.gap_from AND t.ts < g.gap_to)
) x;

-- ---------------------------------------------------------------------
-- 6) status do dispositivo (tópico "status"): a cada 1 h
-- bateria segue o ciclo do painel solar; RSSI oscila em torno da base da estação
-- ---------------------------------------------------------------------
INSERT INTO device_status (station_id, gateway_id, measured_at, received_at, battery_v, battery_pct,
                                     rssi_gateway_dbm, rssi_station_dbm, uptime_gateway_s, uptime_station_s)
SELECT b.station_id, b.gateway_id, b.ts,
       b.ts + make_interval(secs => 1 + floor(random() * 4)),
       round((5.6 + b.pct * 0.013)::numeric, 2)::real,
       b.pct,
       least(b.rssi_gt + floor(random() * 9)::int - 4, -30),
       least(b.rssi_pl + floor(random() * 7)::int - 3, -30),
       extract(epoch FROM b.ts - date_trunc('quarter', b.ts, 'America/Sao_Paulo'))::bigint, -- gateway reinicia a cada trimestre
       extract(epoch FROM b.ts - date_trunc('month', b.ts, 'America/Sao_Paulo'))::bigint    -- pluviômetro todo mês
FROM (
    SELECT s.id AS station_id, s.gateway_id, s.rssi_gt, s.rssi_pl, t.ts,
           greatest(0, least(100, round(
               72 + 18 * sin(2 * pi() * (extract(hour FROM t.ts AT TIME ZONE 'America/Sao_Paulo') - 10) / 24)
                  + (random() * 8 - 4))))::smallint AS pct
    FROM fake_station s
    CROSS JOIN LATERAL generate_series(s.data_start + interval '1 hour',
                                       date_trunc('hour', now()),
                                       interval '1 hour') AS t(ts)
    WHERE NOT EXISTS (SELECT 1 FROM fake_gap g
                      WHERE g.station_id = s.id AND t.ts >= g.gap_from AND t.ts < g.gap_to)
) b;

-- ---------------------------------------------------------------------
-- 7) histórico do conf: instalação (fw 1.0.0) e atualização (fw 1.1.0)
-- ---------------------------------------------------------------------
INSERT INTO config_reports (station_id, gateway_id, reported_at, fw_gateway, hw_gateway,
                                      fw_station, hw_station, send_interval_s, mm_per_tip)
SELECT id, gateway_id, data_start + interval '5 min', '1.0.0', '1.0', '1.0.0', '1.0', 900, mm_per_tip
FROM fake_station
UNION ALL
SELECT id, gateway_id, '2026-06-15 09:00:00-03', '1.1.0', '1.0', '1.1.0', '1.0', 900, mm_per_tip
FROM fake_station;

-- ---------------------------------------------------------------------
-- resumo do que foi gerado
-- ---------------------------------------------------------------------
SELECT s.name,
       extract(year FROM r.measured_at AT TIME ZONE 'America/Sao_Paulo') AS year,
       count(*)                     AS readings,
       sum(r.precipitation_mm)      AS total_mm,
       max(r.precipitation_mm)      AS max_15min_mm
FROM readings r
JOIN fake_station s ON s.id = r.station_id
GROUP BY s.name, year
ORDER BY s.name, year;

COMMIT;
