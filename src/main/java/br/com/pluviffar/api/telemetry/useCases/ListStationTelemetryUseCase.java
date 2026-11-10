package br.com.pluviffar.api.telemetry.useCases;

import br.com.pluviffar.api.shared.web.PageResponse;
import br.com.pluviffar.api.station.useCases.FindStationUseCase;
import br.com.pluviffar.api.telemetry.dtos.ConfigReportResponse;
import br.com.pluviffar.api.telemetry.dtos.DeviceStatusResponse;
import br.com.pluviffar.api.telemetry.dtos.ReadingResponse;
import br.com.pluviffar.api.telemetry.repositories.ConfigReportRepository;
import br.com.pluviffar.api.telemetry.repositories.DeviceStatusRepository;
import br.com.pluviffar.api.telemetry.repositories.ReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Dados brutos paginados de uma estação (leituras, status e histórico de configuração), sempre do mais recente
 * para o mais antigo (o parâmetro sort é ignorado).
 */
@Service
@RequiredArgsConstructor
public class ListStationTelemetryUseCase {

    static final Duration DEFAULT_LOOKBACK = Duration.ofDays(1);
    static final Duration MAX_RANGE = Duration.ofDays(93);

    private final FindStationUseCase findStationUseCase;
    private final ReadingRepository readingRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final ConfigReportRepository configReportRepository;

    @Transactional(readOnly = true)
    public PageResponse<ReadingResponse> readings(UUID stationId, Instant from, Instant to, Pageable pageable) {
        findStationUseCase.getReadableOrThrow(stationId);
        TimeRange range = TimeRange.resolve(from, to, DEFAULT_LOOKBACK, MAX_RANGE);
        return PageResponse.of(readingRepository.findInRange(stationId, range.from(), range.to(),
                newestFirst(pageable, "id.measuredAt")), ReadingResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceStatusResponse> status(UUID stationId, Instant from, Instant to, Pageable pageable) {
        findStationUseCase.getReadableOrThrow(stationId);
        TimeRange range = TimeRange.resolve(from, to, DEFAULT_LOOKBACK, MAX_RANGE);
        return PageResponse.of(deviceStatusRepository.findInRange(stationId, range.from(), range.to(),
                newestFirst(pageable, "id.measuredAt")), DeviceStatusResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConfigReportResponse> configReports(UUID stationId, Pageable pageable) {
        findStationUseCase.getReadableOrThrow(stationId);
        return PageResponse.of(configReportRepository.findByStationId(stationId, newestFirst(pageable, "reportedAt")),
                ConfigReportResponse::from);
    }

    private static Pageable newestFirst(Pageable pageable, String timeProperty) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, timeProperty));
    }
}
