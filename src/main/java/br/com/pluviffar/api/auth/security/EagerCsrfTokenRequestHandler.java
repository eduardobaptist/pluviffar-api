package br.com.pluviffar.api.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.util.function.Supplier;

/**
 * CSRF para SPA: o front lê o cookie XSRF-TOKEN e devolve o valor no header X-XSRF-TOKEN.
 * - Token "cru" (sem a máscara XOR anti-BREACH): o valor do cookie, o do header e o de GET /api/auth/csrf são iguais.
 *   A máscara só protege tokens renderizados em HTML comprimido, o que não acontece aqui.
 * - Carregamento imediato: o cookie é (re)emitido em toda resposta, inclusive logo após o login (quando o token é rotacionado).
 */
public class EagerCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        super.handle(request, response, csrfToken);
        csrfToken.get();
    }
}
