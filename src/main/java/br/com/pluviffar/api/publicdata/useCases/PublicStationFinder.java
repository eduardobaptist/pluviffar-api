package br.com.pluviffar.api.publicdata.useCases;

import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.station.entities.Station;
import br.com.pluviffar.api.station.repositories.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Única porta de entrada da área pública para uma estação: só públicas e não excluídas; o resto é 404. */
@Component
@RequiredArgsConstructor
public class PublicStationFinder {

    private final StationRepository stationRepository;

    public Station getOrThrow(UUID id) {
        return stationRepository.findOne(StationRepository.publiclyVisible().and(StationRepository.byId(id)))
                .orElseThrow(() -> new BusinessException(ErrorCode.STATION_NOT_FOUND));
    }
}
