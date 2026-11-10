package br.com.pluviffar.api.auth.useCases;

import br.com.pluviffar.api.auth.dtos.ChangePasswordRequest;
import br.com.pluviffar.api.auth.security.AuthenticatedUser;
import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.auth.security.UserSessionRevoker;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Troca da própria senha: confere a atual, grava a nova, desliga must_change_password, revoga as OUTRAS sessões
 * e atualiza o principal da sessão atual (liberando a API).
 */
@Service
@RequiredArgsConstructor
public class ChangeOwnPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionRevoker sessionRevoker;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    @Transactional
    public void execute(ChangePasswordRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthenticatedUser principal = CurrentUser.get();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);

        HttpSession session = httpRequest.getSession(false);
        sessionRevoker.revokeAllExcept(user.getId(), session != null ? session.getId() : null);

        Authentication current = contextHolder.getContext().getAuthentication();
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal.withPasswordChanged(), null, current.getAuthorities()));
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }
}
