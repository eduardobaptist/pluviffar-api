package br.com.pluviffar.api.gateway.useCases;

import br.com.pluviffar.api.auth.security.AuthenticatedUser;
import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Decide o dono de um gateway a partir do ownerId do corpo, sem confiar no cliente:
 * PRODUCER é sempre dono do que cadastra (ownerId de outro → 403); ADMIN escolhe um PRODUCER ativo.
 */
@Component
@RequiredArgsConstructor
public class GatewayOwnerResolver {

    private final UserRepository userRepository;

    /** Dono no cadastro: ADMIN precisa informar; PRODUCER é ele mesmo. */
    public User resolveForCreate(UUID requestedOwnerId) {
        AuthenticatedUser me = CurrentUser.get();
        if (!me.isAdmin()) {
            requireSelf(me, requestedOwnerId);
            return userRepository.getReferenceById(me.getId());
        }
        if (requestedOwnerId == null) {
            throw new BusinessException(ErrorCode.OWNER_REQUIRED);
        }
        return requireActiveProducer(requestedOwnerId);
    }

    /** Novo dono numa edição, ou null para manter o atual. Só ADMIN transfere. */
    public User resolveForTransfer(UUID requestedOwnerId, User currentOwner) {
        AuthenticatedUser me = CurrentUser.get();
        if (requestedOwnerId == null || requestedOwnerId.equals(currentOwner.getId())) {
            return null;
        }
        if (!me.isAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return requireActiveProducer(requestedOwnerId);
    }

    private static void requireSelf(AuthenticatedUser me, UUID requestedOwnerId) {
        if (requestedOwnerId != null && !requestedOwnerId.equals(me.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private User requireActiveProducer(UUID ownerId) {
        return userRepository.findById(ownerId)
                .filter(u -> u.getRole() == Role.PRODUCER && u.isActive())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OWNER));
    }
}
