package br.com.pluviffar.api.telemetry.useCases;

import br.com.pluviffar.api.station.useCases.FindStationUseCase;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationBucket;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationSeriesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetStationPrecipitationUseCase {

    private final FindStationUseCase findStationUseCase;
    private final PrecipitationReport precipitationReport;

    @Transactional(readOnly = true)
    public PrecipitationSeriesResponse execute(UUID stationId, PrecipitationBucket bucket, Instant from, Instant to,
                                               String timezone) {
        findStationUseCase.getReadableOrThrow(stationId);
        return precipitationReport.series(stationId, bucket, from, to, timezone);
    }
}
