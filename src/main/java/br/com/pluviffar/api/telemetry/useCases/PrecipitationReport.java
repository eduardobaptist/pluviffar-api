package br.com.pluviffar.api.telemetry.useCases;

import br.com.pluviffar.api.telemetry.dtos.PrecipitationBucket;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationPoint;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationSeriesResponse;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationTotals;
import br.com.pluviffar.api.telemetry.repositories.TelemetryQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Relatórios de chuva de uma estação. NÃO checa acesso: quem chama já validou a estação
 * (posse na área logada, is_public na área pública).
 */
@Component
@RequiredArgsConstructor
public class PrecipitationReport {

    private final TelemetryQueries telemetryQueries;

    public PrecipitationSeriesResponse series(UUID stationId, PrecipitationBucket bucket, Instant from, Instant to,
                                              String timezone) {
        PrecipitationBucket effective = bucket != null ? bucket : PrecipitationBucket.DAY;
        ZoneId zone = TimeRange.zone(timezone);
        TimeRange range = TimeRange.resolve(from, to, effective.getDefaultLookback(), effective.getMaxRange());

        List<PrecipitationPoint> points = telemetryQueries
                .precipitationSeries(stationId, range.from(), range.to(), effective.getSqlInterval(), zone).stream()
                .map(row -> new PrecipitationPoint(row.bucketStart(), row.precipitationMm(), row.readingsCount()))
                .toList();
        BigDecimal total = points.stream().map(PrecipitationPoint::precipitationMm).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PrecipitationSeriesResponse(stationId, effective, zone.getId(), range.from(), range.to(), total, points);
    }

    public PrecipitationTotals totals(UUID stationId, ZoneId zone) {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return PrecipitationTotals.from(telemetryQueries.precipitationTotals(stationId, now, startOfToday));
    }
}
