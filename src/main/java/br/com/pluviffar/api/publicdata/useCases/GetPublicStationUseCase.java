package br.com.pluviffar.api.publicdata.useCases;

import br.com.pluviffar.api.publicdata.dtos.PublicStationDetail;
import br.com.pluviffar.api.station.entities.Station;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationBucket;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationSeriesResponse;
import br.com.pluviffar.api.telemetry.repositories.ReadingRepository;
import br.com.pluviffar.api.telemetry.useCases.PrecipitationReport;
import br.com.pluviffar.api.telemetry.useCases.TimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Detalhe e série de chuva de uma estação pública (mesmos cálculos da área logada, sem dados do dono). */
@Service
@RequiredArgsConstructor
public class GetPublicStationUseCase {

    private final PublicStationFinder publicStationFinder;
    private final ReadingRepository readingRepository;
    private final PrecipitationReport precipitationReport;

    @Transactional(readOnly = true)
    public PublicStationDetail detail(UUID id, String timezone) {
        Station station = publicStationFinder.getOrThrow(id);
        ZoneId zone = TimeRange.zone(timezone);
        Instant lastReadingAt = readingRepository.findFirstByIdStationIdOrderByIdMeasuredAtDesc(id)
                .map(r -> r.getId().getMeasuredAt()).orElse(null);
        return new PublicStationDetail(station.getId(), station.getName(), station.getLatitude(), station.getLongitude(),
                lastReadingAt, zone.getId(), precipitationReport.totals(id, zone));
    }

    @Transactional(readOnly = true)
    public PrecipitationSeriesResponse precipitation(UUID id, PrecipitationBucket bucket, Instant from, Instant to,
                                                     String timezone) {
        publicStationFinder.getOrThrow(id);
        return precipitationReport.series(id, bucket, from, to, timezone);
    }
}
