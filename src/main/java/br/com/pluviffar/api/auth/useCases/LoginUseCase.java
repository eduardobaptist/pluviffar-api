package br.com.pluviffar.api.auth.useCases;

import br.com.pluviffar.api.auth.dtos.CurrentUserResponse;
import br.com.pluviffar.api.auth.dtos.LoginRequest;
import br.com.pluviffar.api.auth.security.AuthenticatedUser;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Login JSON: autentica, troca o ID da sessão e rotaciona o token CSRF (SessionAuthenticationStrategy),
 * e salva o SecurityContext na sessão (persistida no Postgres pelo Spring Session).
 */
@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final UserRepository userRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public CurrentUserResponse execute(LoginRequest request, HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(User.normalizeEmail(request.email()),
                            request.password()));
        } catch (AuthenticationException ex) {
            // mensagem única para email inexistente, senha errada ou usuário desativado (não revela se o usuário existe)
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return userRepository.findById(principal.getId())
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
    }
}
