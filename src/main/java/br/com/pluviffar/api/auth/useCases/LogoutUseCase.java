package br.com.pluviffar.api.auth.useCases;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.stereotype.Service;

/**
 * Logout: invalida a sessão (o Spring Session apaga a linha e expira o cookie SESSION) e descarta o token CSRF.
 * Idempotente: sem sessão, apenas responde 204.
 */
@Service
public class LogoutUseCase {

    private final SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
    private final CsrfLogoutHandler csrfLogoutHandler;

    public LogoutUseCase(CookieCsrfTokenRepository csrfTokenRepository) {
        this.csrfLogoutHandler = new CsrfLogoutHandler(csrfTokenRepository);
    }

    public void execute(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        securityContextLogoutHandler.logout(request, response, authentication);
        csrfLogoutHandler.logout(request, response, authentication);
    }
}
