package br.com.pluviffar.api.user.useCases;

import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.auth.security.UserSessionRevoker;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.dtos.UserResponse;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** ADMIN ativa/desativa usuário. Desativar revoga todas as sessões na hora. */
@Service
@RequiredArgsConstructor
public class UpdateUserStatusUseCase {

    private final UserRepository userRepository;
    private final FindUserUseCase findUserUseCase;
    private final UserSessionRevoker sessionRevoker;

    @Transactional
    public UserResponse execute(UUID id, boolean active) {
        if (!active && CurrentUser.get().getId().equals(id)) {
            throw new BusinessException(ErrorCode.CANNOT_DEACTIVATE_SELF);
        }
        User user = findUserUseCase.getOrThrow(id);
        user.setActive(active);
        userRepository.saveAndFlush(user);
        if (!active) {
            sessionRevoker.revokeAll(id);
        }
        return UserResponse.from(user);
    }
}
