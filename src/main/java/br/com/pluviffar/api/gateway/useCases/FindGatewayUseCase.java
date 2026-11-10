package br.com.pluviffar.api.gateway.useCases;

import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.gateway.dtos.GatewayResponse;
import br.com.pluviffar.api.gateway.entities.Gateway;
import br.com.pluviffar.api.gateway.repositories.GatewayRepository;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Busca com checagem de posse: gateway de outro produtor (ou excluído) responde 404, sem revelar que existe.
 * O ADMIN também enxerga excluídos no detalhe, mas não pode alterá-los.
 */
@Service
@RequiredArgsConstructor
public class FindGatewayUseCase {

    private final GatewayRepository gatewayRepository;

    @Transactional(readOnly = true)
    public GatewayResponse execute(UUID id) {
        UUID scope = CurrentUser.ownerScope();
        return GatewayResponse.from(find(id, scope, scope == null));
    }

    /** Gateway ativo acessível pelo usuário atual (para alterar ou vincular estações). */
    public Gateway getActiveOrThrow(UUID id) {
        return find(id, CurrentUser.ownerScope(), false);
    }

    private Gateway find(UUID id, UUID scope, boolean includeDeleted) {
        return gatewayRepository.findOne(GatewayRepository.visible(scope, includeDeleted).and(GatewayRepository.byId(id)))
                .orElseThrow(() -> new BusinessException(ErrorCode.GATEWAY_NOT_FOUND));
    }
}
