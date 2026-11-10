package br.com.pluviffar.api.telemetry.useCases;

import br.com.pluviffar.api.station.useCases.FindStationUseCase;
import br.com.pluviffar.api.telemetry.dtos.DeviceStatusResponse;
import br.com.pluviffar.api.telemetry.dtos.ReadingResponse;
import br.com.pluviffar.api.telemetry.dtos.StationOverviewResponse;
import br.com.pluviffar.api.telemetry.repositories.DeviceStatusRepository;
import br.com.pluviffar.api.telemetry.repositories.ReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetStationOverviewUseCase {

    private final FindStationUseCase findStationUseCase;
    private final ReadingRepository readingRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final PrecipitationReport precipitationReport;

    @Transactional(readOnly = true)
    public StationOverviewResponse execute(UUID stationId, String timezone) {
        findStationUseCase.getReadableOrThrow(stationId);
        ZoneId zone = TimeRange.zone(timezone);
        return new StationOverviewResponse(
                stationId,
                zone.getId(),
                readingRepository.findFirstByIdStationIdOrderByIdMeasuredAtDesc(stationId).map(ReadingResponse::from).orElse(null),
                deviceStatusRepository.findFirstByIdStationIdOrderByIdMeasuredAtDesc(stationId).map(DeviceStatusResponse::from).orElse(null),
                precipitationReport.totals(stationId, zone));
    }
}
