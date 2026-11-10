package br.com.pluviffar.api.station.useCases;

import br.com.pluviffar.api.gateway.useCases.FindGatewayUseCase;
import br.com.pluviffar.api.station.dtos.StationResponse;
import br.com.pluviffar.api.station.dtos.UpdateStationRequest;
import br.com.pluviffar.api.station.entities.Station;
import br.com.pluviffar.api.station.repositories.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Edita a estação; trocar de gateway exige um gateway acessível e a estação herda o dono dele. */
@Service
@RequiredArgsConstructor
public class UpdateStationUseCase {

    private final StationRepository stationRepository;
    private final FindStationUseCase findStationUseCase;
    private final FindGatewayUseCase findGatewayUseCase;

    @Transactional
    public StationResponse execute(UUID id, UpdateStationRequest request) {
        Station station = findStationUseCase.getActiveOrThrow(id);
        if (!station.getGateway().getId().equals(request.gatewayId())) {
            station.assignGateway(findGatewayUseCase.getActiveOrThrow(request.gatewayId()));
        }
        station.setName(request.name());
        station.setDescription(request.description());
        station.setLatitude(request.latitude());
        station.setLongitude(request.longitude());
        station.setPublic(request.isPublic());
        return StationResponse.from(stationRepository.saveAndFlush(station));
    }
}
