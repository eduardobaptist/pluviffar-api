package br.com.pluviffar.api.user.useCases;

import br.com.pluviffar.api.auth.security.UserSessionRevoker;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** ADMIN redefine a senha (provisória: troca obrigatória no próximo login) e revoga todas as sessões do usuário. */
@Service
@RequiredArgsConstructor
public class ResetUserPasswordUseCase {

    private final UserRepository userRepository;
    private final FindUserUseCase findUserUseCase;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionRevoker sessionRevoker;

    @Transactional
    public void execute(UUID id, String newPassword) {
        User user = findUserUseCase.getOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.saveAndFlush(user);
        sessionRevoker.revokeAll(id);
    }
}
