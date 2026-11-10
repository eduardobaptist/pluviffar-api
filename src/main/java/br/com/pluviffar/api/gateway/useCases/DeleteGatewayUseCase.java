package br.com.pluviffar.api.gateway.useCases;

import br.com.pluviffar.api.gateway.entities.Gateway;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.station.repositories.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Soft delete: a telemetria nunca é apagada e o chip ID fica livre para novo cadastro.
 * Bloqueado enquanto houver estações ativas (o worker passaria a descartar os pacotes delas).
 */
@Service
@RequiredArgsConstructor
public class DeleteGatewayUseCase {

    private final FindGatewayUseCase findGatewayUseCase;
    private final StationRepository stationRepository;

    @Transactional
    public void execute(UUID id) {
        Gateway gateway = findGatewayUseCase.getActiveOrThrow(id);
        if (stationRepository.existsByGateway_IdAndDeletedAtIsNull(gateway.getId())) {
            throw new BusinessException(ErrorCode.GATEWAY_HAS_ACTIVE_STATIONS);
        }
        gateway.setDeletedAt(Instant.now());
    }
}
