package br.com.pluviffar.api.user.useCases;

import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.dtos.CreateUserRequest;
import br.com.pluviffar.api.user.dtos.UserResponse;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ADMIN cadastra usuário com senha provisória (troca obrigatória no primeiro login). */
@Service
@RequiredArgsConstructor
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse execute(CreateUserRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_IN_USE);
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()), request.role());
        user.setMustChangePassword(true);
        return UserResponse.from(userRepository.saveAndFlush(user));
    }
}
