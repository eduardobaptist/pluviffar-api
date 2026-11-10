package br.com.pluviffar.api.auth.useCases;

import br.com.pluviffar.api.auth.dtos.CurrentUserResponse;
import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dados atuais do usuário logado, lidos do banco (não do principal da sessão, que pode estar desatualizado). */
@Service
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CurrentUserResponse execute() {
        return userRepository.findById(CurrentUser.get().getId())
                .filter(User::isActive)
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
