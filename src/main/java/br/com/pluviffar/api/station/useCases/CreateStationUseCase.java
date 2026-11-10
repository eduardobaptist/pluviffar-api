package br.com.pluviffar.api.station.useCases;

import br.com.pluviffar.api.gateway.entities.Gateway;
import br.com.pluviffar.api.gateway.useCases.FindGatewayUseCase;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.station.dtos.CreateStationRequest;
import br.com.pluviffar.api.station.dtos.StationResponse;
import br.com.pluviffar.api.station.entities.Station;
import br.com.pluviffar.api.station.repositories.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O dono da estação é sempre o dono do gateway; gateway de outro produtor responde 404. */
@Service
@RequiredArgsConstructor
public class CreateStationUseCase {

    private final StationRepository stationRepository;
    private final FindGatewayUseCase findGatewayUseCase;

    @Transactional
    public StationResponse execute(CreateStationRequest request) {
        Gateway gateway = findGatewayUseCase.getActiveOrThrow(request.gatewayId());
        if (stationRepository.existsByHardwareIdAndDeletedAtIsNull(request.hardwareId())) {
            throw new BusinessException(ErrorCode.HARDWARE_ID_ALREADY_REGISTERED);
        }
        Station station = new Station(request.hardwareId(), gateway, request.name());
        station.setDescription(request.description());
        station.setLatitude(request.latitude());
        station.setLongitude(request.longitude());
        station.setPublic(request.isPublic());
        return StationResponse.from(stationRepository.saveAndFlush(station));
    }
}
