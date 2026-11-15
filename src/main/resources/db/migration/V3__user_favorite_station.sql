-- =====================================================================
-- PluvIFFar — V3: estação favorita do usuário
-- Uma por usuário; o front usa para destacar a estação (ex.: primeira no dashboard).
-- PRODUCER só favorita as próprias (checado na API). Estação excluída ou transferida
-- some das listagens do usuário, então o favorito antigo não aparece.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN favorite_station_id uuid,
    ADD CONSTRAINT users_favorite_station_fk
        FOREIGN KEY (favorite_station_id) REFERENCES stations (id) ON DELETE SET NULL;
