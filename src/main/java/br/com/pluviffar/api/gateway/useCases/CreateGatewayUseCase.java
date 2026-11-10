package br.com.pluviffar.api.gateway.useCases;

import br.com.pluviffar.api.gateway.dtos.CreateGatewayRequest;
import br.com.pluviffar.api.gateway.dtos.GatewayResponse;
import br.com.pluviffar.api.gateway.entities.Gateway;
import br.com.pluviffar.api.gateway.repositories.GatewayRepository;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.entities.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateGatewayUseCase {

    private final GatewayRepository gatewayRepository;
    private final GatewayOwnerResolver ownerResolver;

    @Transactional
    public GatewayResponse execute(CreateGatewayRequest request) {
        User owner = ownerResolver.resolveForCreate(request.ownerId());
        if (gatewayRepository.existsByHardwareIdAndDeletedAtIsNull(request.hardwareId())) {
            throw new BusinessException(ErrorCode.HARDWARE_ID_ALREADY_REGISTERED);
        }
        Gateway gateway = new Gateway(request.hardwareId(), owner, request.name());
        gateway.setDescription(request.description());
        return GatewayResponse.from(gatewayRepository.saveAndFlush(gateway));
    }
}
