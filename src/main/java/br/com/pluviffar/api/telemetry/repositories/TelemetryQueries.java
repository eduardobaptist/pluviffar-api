package br.com.pluviffar.api.telemetry.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultas de série temporal em SQL nativo (time_bucket do TimescaleDB, edição Apache), fora do JPQL.
 * Intervalos sempre semiabertos: [from, to).
 */
@Repository
@RequiredArgsConstructor
public class TelemetryQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public record PrecipitationBucketRow(Instant bucketStart, BigDecimal precipitationMm, long readingsCount) {
    }

    public record PrecipitationTotalsRow(BigDecimal todayMm, BigDecimal last24hMm, BigDecimal last7dMm,
                                         BigDecimal last30dMm) {
    }

    public record StationLatestRow(UUID stationId, Instant lastReadingAt, BigDecimal last24hMm) {
    }

    /**
     * Chuva agregada por bucket no fuso informado (ex.: dias começam à meia-noite de America/Sao_Paulo).
     * Só devolve buckets com leituras: ausência de dado não é "zero chuva".
     *
     * @param bucketWidth intervalo do PostgreSQL ("1 hour", "1 day", "1 week", "1 month")
     */
    public List<PrecipitationBucketRow> precipitationSeries(UUID stationId, Instant from, Instant to,
                                                            String bucketWidth, ZoneId zone) {
        String sql = """
                SELECT time_bucket(CAST(:width AS interval), measured_at, :tz) AS bucket_start,
                       sum(precipitation_mm) AS precipitation_mm,
                       count(*)              AS readings_count
                  FROM readings
                 WHERE station_id = :stationId
                   AND measured_at >= :from AND measured_at < :to
                 GROUP BY bucket_start
                 ORDER BY bucket_start
                """;
        var params = new MapSqlParameterSource()
                .addValue("width", bucketWidth)
                .addValue("tz", zone.getId())
                .addValue("stationId", stationId)
                .addValue("from", utc(from))
                .addValue("to", utc(to));
        return jdbc.query(sql, params, (rs, i) -> new PrecipitationBucketRow(
                instant(rs, "bucket_start"), rs.getBigDecimal("precipitation_mm"), rs.getLong("readings_count")));
    }

    /** Totais de chuva até "now": desde a meia-noite local (hoje), 24 h, 7 dias e 30 dias. */
    public PrecipitationTotalsRow precipitationTotals(UUID stationId, Instant now, Instant startOfToday) {
        String sql = """
                SELECT coalesce(sum(precipitation_mm) FILTER (WHERE measured_at >= :today), 0) AS today_mm,
                       coalesce(sum(precipitation_mm) FILTER (WHERE measured_at >= :d1), 0)    AS last24h_mm,
                       coalesce(sum(precipitation_mm) FILTER (WHERE measured_at >= :d7), 0)    AS last7d_mm,
                       coalesce(sum(precipitation_mm), 0)                                     AS last30d_mm
                  FROM readings
                 WHERE station_id = :stationId
                   AND measured_at >= :d30 AND measured_at <= :now
                """;
        var params = new MapSqlParameterSource()
                .addValue("stationId", stationId)
                .addValue("today", utc(startOfToday))
                .addValue("d1", utc(now.minusSeconds(86_400)))
                .addValue("d7", utc(now.minusSeconds(7 * 86_400)))
                .addValue("d30", utc(now.minusSeconds(30 * 86_400)))
                .addValue("now", utc(now));
        return jdbc.queryForObject(sql, params, (rs, i) -> new PrecipitationTotalsRow(
                rs.getBigDecimal("today_mm"), rs.getBigDecimal("last24h_mm"),
                rs.getBigDecimal("last7d_mm"), rs.getBigDecimal("last30d_mm")));
    }

    /** Última leitura e chuva das últimas 24 h de várias estações (uma consulta, para listagens/mapa). */
    public Map<UUID, StationLatestRow> latestByStation(Collection<UUID> stationIds, Instant now) {
        if (stationIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT s.id AS station_id,
                       (SELECT r.measured_at FROM readings r
                         WHERE r.station_id = s.id AND r.measured_at <= :now
                         ORDER BY r.measured_at DESC LIMIT 1) AS last_reading_at,
                       (SELECT coalesce(sum(r.precipitation_mm), 0) FROM readings r
                         WHERE r.station_id = s.id AND r.measured_at >= :d1 AND r.measured_at <= :now) AS last24h_mm
                  FROM stations s
                 WHERE s.id IN (:ids)
                """;
        var params = new MapSqlParameterSource()
                .addValue("ids", stationIds)
                .addValue("d1", utc(now.minusSeconds(86_400)))
                .addValue("now", utc(now));
        return jdbc.query(sql, params, (rs, i) -> new StationLatestRow(
                        rs.getObject("station_id", UUID.class), instant(rs, "last_reading_at"),
                        rs.getBigDecimal("last24h_mm")))
                .stream().collect(Collectors.toMap(StationLatestRow::stationId, Function.identity()));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
