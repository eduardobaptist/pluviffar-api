package br.com.pluviffar.api.user.useCases;

import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.auth.security.UserSessionRevoker;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.dtos.UpdateUserRequest;
import br.com.pluviffar.api.user.dtos.UserResponse;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** ADMIN edita nome, email e perfil. Trocar o perfil revoga as sessões (o principal da sessão guarda a role). */
@Service
@RequiredArgsConstructor
public class UpdateUserUseCase {

    private final UserRepository userRepository;
    private final FindUserUseCase findUserUseCase;
    private final UserSessionRevoker sessionRevoker;

    @Transactional
    public UserResponse execute(UUID id, UpdateUserRequest request) {
        User user = findUserUseCase.getOrThrow(id);
        boolean roleChanged = user.getRole() != request.role();
        if (roleChanged && CurrentUser.get().getId().equals(id)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_IN_USE);
        }

        user.setName(request.name().trim());
        user.setEmail(email);
        user.setRole(request.role());
        userRepository.saveAndFlush(user);

        if (roleChanged) {
            sessionRevoker.revokeAll(id);
        }
        return UserResponse.from(user);
    }
}
