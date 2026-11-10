package br.com.pluviffar.api.gateway.useCases;

import br.com.pluviffar.api.gateway.dtos.GatewayResponse;
import br.com.pluviffar.api.gateway.dtos.UpdateGatewayRequest;
import br.com.pluviffar.api.gateway.entities.Gateway;
import br.com.pluviffar.api.gateway.repositories.GatewayRepository;
import br.com.pluviffar.api.user.entities.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Edita nome/descrição. Transferência de dono (só ADMIN): o banco propaga o novo dono para as estações
 * do gateway (FK composta ON UPDATE CASCADE).
 */
@Service
@RequiredArgsConstructor
public class UpdateGatewayUseCase {

    private final GatewayRepository gatewayRepository;
    private final FindGatewayUseCase findGatewayUseCase;
    private final GatewayOwnerResolver ownerResolver;
    private final EntityManager entityManager;

    @Transactional
    public GatewayResponse execute(UUID id, UpdateGatewayRequest request) {
        Gateway gateway = findGatewayUseCase.getActiveOrThrow(id);
        User newOwner = ownerResolver.resolveForTransfer(request.ownerId(), gateway.getOwner());

        gateway.setName(request.name());
        gateway.setDescription(request.description());
        if (newOwner != null) {
            gateway.setOwner(newOwner);
        }
        gatewayRepository.saveAndFlush(gateway);
        if (newOwner != null) {
            // estações carregadas nesta transação ficariam com o dono antigo em memória
            entityManager.clear();
            gateway = findGatewayUseCase.getActiveOrThrow(id);
        }
        return GatewayResponse.from(gateway);
    }
}
