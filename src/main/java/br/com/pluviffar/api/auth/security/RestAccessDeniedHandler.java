package br.com.pluviffar.api.auth.security;

import br.com.pluviffar.api.shared.exceptions.ApiErrorWriter;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 403 em JSON. Token CSRF ausente/inválido ganha código próprio para o front renovar o token. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter errorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        errorWriter.write(response, ex instanceof CsrfException ? ErrorCode.CSRF_TOKEN_INVALID : ErrorCode.ACCESS_DENIED);
    }
}
