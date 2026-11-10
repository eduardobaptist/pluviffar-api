package br.com.pluviffar.api.publicdata.useCases;

import br.com.pluviffar.api.publicdata.dtos.PublicStationSummary;
import br.com.pluviffar.api.station.entities.Station;
import br.com.pluviffar.api.station.repositories.StationRepository;
import br.com.pluviffar.api.telemetry.repositories.TelemetryQueries;
import br.com.pluviffar.api.telemetry.repositories.TelemetryQueries.StationLatestRow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Todas as estações públicas (sem paginação: alimenta o mapa), com última leitura e chuva de 24 h. */
@Service
@RequiredArgsConstructor
public class ListPublicStationsUseCase {

    private final StationRepository stationRepository;
    private final TelemetryQueries telemetryQueries;

    @Transactional(readOnly = true)
    public List<PublicStationSummary> execute() {
        List<Station> stations = stationRepository.findAll(StationRepository.publiclyVisible(), Sort.by("name"));
        Map<UUID, StationLatestRow> latest = telemetryQueries.latestByStation(
                stations.stream().map(Station::getId).toList(), Instant.now());
        return stations.stream().map(s -> {
            StationLatestRow row = latest.get(s.getId());
            return new PublicStationSummary(s.getId(), s.getName(), s.getLatitude(), s.getLongitude(),
                    row == null ? null : row.lastReadingAt(), row == null ? BigDecimal.ZERO : row.last24hMm());
        }).toList();
    }
}
