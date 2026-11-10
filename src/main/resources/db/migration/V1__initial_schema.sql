-- =====================================================================
-- PluvIFFar — V1: schema inicial (redesign)
--   app        : tabelas da aplicação (usuários, gateways, estações, sessões)
--   telemetry  : dados vindos do MQTT (gravados pelo worker)
-- Os schemas são criados pelo Flyway (spring.flyway.schemas=app,telemetry).
-- O schema legado "dados" não é tocado.
-- =====================================================================

-- guarda: a extensão já deve existir (na VPS o usuário não é superuser e não pode criá-la)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        RAISE EXCEPTION 'Extensão timescaledb não instalada neste banco; peça a um superusuário: CREATE EXTENSION timescaledb;';
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- app.users — usuário único; perfil diferenciado pela role
-- ---------------------------------------------------------------------
CREATE TABLE app.users (
    id                   uuid         NOT NULL DEFAULT uuidv7(),
    name                 varchar(120) NOT NULL,
    email                varchar(254) NOT NULL,
    password_hash        varchar(100) NOT NULL,                 -- BCrypt
    role                 varchar(20)  NOT NULL,
    active               boolean      NOT NULL DEFAULT true,
    must_change_password boolean      NOT NULL DEFAULT true,    -- troca obrigatória no primeiro login
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_email_lower_ck CHECK (email = lower(email)), -- case-insensitive sem citext
    CONSTRAINT users_role_ck CHECK (role IN ('ADMIN', 'PRODUCER'))
);

-- ---------------------------------------------------------------------
-- app.gateways — ESP8266 que publica no broker; hardware_id = segmento do tópico (ex.: gt-570db4)
-- ---------------------------------------------------------------------
CREATE TABLE app.gateways (
    id                 uuid         NOT NULL DEFAULT uuidv7(),
    hardware_id        varchar(32)  NOT NULL,
    owner_id           uuid         NOT NULL,
    name               varchar(100) NOT NULL,
    description        text,
    fw_version         varchar(20),          -- conf.vers_gt (estado atual)
    hw_version         varchar(20),          -- conf.hw_gt
    config_reported_at timestamptz,          -- último conf recebido
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    deleted_at         timestamptz,          -- soft delete (telemetria nunca é apagada)
    CONSTRAINT gateways_pk PRIMARY KEY (id),
    CONSTRAINT gateways_id_owner_uk UNIQUE (id, owner_id), -- alvo da FK composta de stations
    CONSTRAINT gateways_owner_fk FOREIGN KEY (owner_id) REFERENCES app.users (id) ON DELETE RESTRICT,
    CONSTRAINT gateways_hardware_id_ck CHECK (hardware_id ~ '^[a-z0-9-]{1,32}$')
);

-- chip ID único entre os cadastros ativos (liberado após soft delete)
CREATE UNIQUE INDEX gateways_hardware_id_active_uk ON app.gateways (hardware_id) WHERE deleted_at IS NULL;
CREATE INDEX gateways_owner_ix ON app.gateways (owner_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- app.stations — pluviômetro; hardware_id = campo "id" do payload (ex.: 580db2)
-- ---------------------------------------------------------------------
CREATE TABLE app.stations (
    id                 uuid             NOT NULL DEFAULT uuidv7(),
    hardware_id        varchar(32)      NOT NULL,
    gateway_id         uuid             NOT NULL,
    owner_id           uuid             NOT NULL,   -- denormalizado; sempre igual ao dono do gateway (FK composta)
    name               varchar(100)     NOT NULL,
    description        text,
    latitude           double precision,
    longitude          double precision,
    is_public          boolean          NOT NULL DEFAULT false, -- visível na área pública (sem dados do dono)
    fw_version         varchar(20),                -- conf.vers_pl (estado atual)
    hw_version         varchar(20),                -- conf.hw_pl
    send_interval_s    integer,                    -- conf.tempo_envio
    mm_per_tip         numeric(5, 3),              -- conf.mm_tips (mm por basculada)
    config_reported_at timestamptz,
    created_at         timestamptz      NOT NULL DEFAULT now(),
    updated_at         timestamptz      NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    CONSTRAINT stations_pk PRIMARY KEY (id),
    CONSTRAINT stations_gateway_owner_fk FOREIGN KEY (gateway_id, owner_id)
        REFERENCES app.gateways (id, owner_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT stations_owner_fk FOREIGN KEY (owner_id) REFERENCES app.users (id) ON DELETE RESTRICT,
    CONSTRAINT stations_hardware_id_ck CHECK (hardware_id ~ '^[a-z0-9-]{1,32}$'),
    CONSTRAINT stations_latitude_ck CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT stations_longitude_ck CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT stations_public_location_ck CHECK (NOT is_public OR (latitude IS NOT NULL AND longitude IS NOT NULL)),
    CONSTRAINT stations_send_interval_ck CHECK (send_interval_s > 0),
    CONSTRAINT stations_mm_per_tip_ck CHECK (mm_per_tip > 0)
);

CREATE UNIQUE INDEX stations_hardware_id_active_uk ON app.stations (hardware_id) WHERE deleted_at IS NULL;
CREATE INDEX stations_owner_ix ON app.stations (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX stations_gateway_ix ON app.stations (gateway_id);
CREATE INDEX stations_public_ix ON app.stations (id) WHERE is_public AND deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- Spring Session JDBC — cópia do schema-postgresql.sql oficial (spring-session-jdbc 4.1.1), no schema app
-- ---------------------------------------------------------------------
CREATE TABLE app.SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON app.SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON app.SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON app.SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE app.SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES app.SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
-- telemetry.readings — tópico "dados" (hypertable)
-- PK (station_id, measured_at) deduplica: worker usa INSERT ... ON CONFLICT DO NOTHING
-- ---------------------------------------------------------------------
CREATE TABLE telemetry.readings (
    station_id       uuid          NOT NULL,
    measured_at      timestamptz   NOT NULL,                -- dados.timestamp (epoch UTC)
    received_at      timestamptz   NOT NULL DEFAULT now(),  -- quando o worker gravou
    tips_per_minute  integer       NOT NULL,                -- dados.tips_por_minuto
    precipitation_mm numeric(6, 2) NOT NULL,                -- dados.mm: chuva no intervalo (exato p/ somar)
    seq              bigint        NOT NULL,                -- dados.seq: detectar perda/duplicidade (zera no reboot)
    CONSTRAINT readings_pk PRIMARY KEY (station_id, measured_at),
    CONSTRAINT readings_station_fk FOREIGN KEY (station_id) REFERENCES app.stations (id) ON DELETE RESTRICT,
    CONSTRAINT readings_tips_ck CHECK (tips_per_minute >= 0),
    CONSTRAINT readings_mm_ck CHECK (precipitation_mm >= 0)
);

SELECT create_hypertable('telemetry.readings', by_range('measured_at', INTERVAL '30 days'));

-- ---------------------------------------------------------------------
-- telemetry.device_status — tópico "status" (hypertable)
-- ---------------------------------------------------------------------
CREATE TABLE telemetry.device_status (
    station_id       uuid        NOT NULL,
    gateway_id       uuid        NOT NULL,                -- gateway pelo qual o pacote chegou
    measured_at      timestamptz NOT NULL,                -- status.timestamp
    received_at      timestamptz NOT NULL DEFAULT now(),
    battery_v        real,                                -- status.vbat (volts)
    battery_pct      smallint,                            -- status.bat (%)
    rssi_gateway_dbm smallint,                            -- status.rssi_gt
    rssi_station_dbm smallint,                            -- status.rssi_pluv
    uptime_gateway_s bigint,                              -- status.uptime_gt_s
    uptime_station_s bigint,                              -- status.uptime_pluv_s
    CONSTRAINT device_status_pk PRIMARY KEY (station_id, measured_at),
    CONSTRAINT device_status_station_fk FOREIGN KEY (station_id) REFERENCES app.stations (id) ON DELETE RESTRICT,
    CONSTRAINT device_status_gateway_fk FOREIGN KEY (gateway_id) REFERENCES app.gateways (id) ON DELETE RESTRICT,
    CONSTRAINT device_status_battery_pct_ck CHECK (battery_pct BETWEEN 0 AND 100),
    CONSTRAINT device_status_rssi_gateway_ck CHECK (rssi_gateway_dbm <= 0),
    CONSTRAINT device_status_rssi_station_ck CHECK (rssi_station_dbm <= 0),
    CONSTRAINT device_status_uptime_gateway_ck CHECK (uptime_gateway_s >= 0),
    CONSTRAINT device_status_uptime_station_ck CHECK (uptime_station_s >= 0)
);

SELECT create_hypertable('telemetry.device_status', by_range('measured_at', INTERVAL '30 days'));

CREATE INDEX device_status_gateway_ix ON telemetry.device_status (gateway_id, measured_at DESC);

-- ---------------------------------------------------------------------
-- telemetry.config_reports — histórico do tópico "conf" (tabela comum: mensagem rara e sem timestamp)
-- o worker só insere quando algo muda em relação ao estado atual em stations/gateways
-- ---------------------------------------------------------------------
CREATE TABLE telemetry.config_reports (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    station_id      uuid        NOT NULL,
    gateway_id      uuid        NOT NULL,
    reported_at     timestamptz NOT NULL DEFAULT now(),
    fw_gateway      varchar(20),            -- conf.vers_gt
    hw_gateway      varchar(20),            -- conf.hw_gt
    fw_station      varchar(20),            -- conf.vers_pl
    hw_station      varchar(20),            -- conf.hw_pl
    send_interval_s integer,                -- conf.tempo_envio
    mm_per_tip      numeric(5, 3),          -- conf.mm_tips
    CONSTRAINT config_reports_pk PRIMARY KEY (id),
    CONSTRAINT config_reports_station_fk FOREIGN KEY (station_id) REFERENCES app.stations (id) ON DELETE RESTRICT,
    CONSTRAINT config_reports_gateway_fk FOREIGN KEY (gateway_id) REFERENCES app.gateways (id) ON DELETE RESTRICT
);

CREATE INDEX config_reports_station_ix ON telemetry.config_reports (station_id, reported_at DESC);

-- ---------------------------------------------------------------------
-- Compressão: só existe na licença TSL ("timescale"). Produção e dev usam "apache" → bloco pulado.
-- Sem política de retenção: dado medido não se apaga.
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF current_setting('timescaledb.license', true) = 'timescale' THEN
        ALTER TABLE telemetry.readings SET (
            timescaledb.compress,
            timescaledb.compress_segmentby = 'station_id',
            timescaledb.compress_orderby = 'measured_at DESC');
        PERFORM add_compression_policy('telemetry.readings', INTERVAL '90 days');

        ALTER TABLE telemetry.device_status SET (
            timescaledb.compress,
            timescaledb.compress_segmentby = 'station_id',
            timescaledb.compress_orderby = 'measured_at DESC');
        PERFORM add_compression_policy('telemetry.device_status', INTERVAL '90 days');
    ELSE
        RAISE NOTICE 'TimescaleDB edição "%": compressão não configurada', current_setting('timescaledb.license', true);
    END IF;
END $$;
