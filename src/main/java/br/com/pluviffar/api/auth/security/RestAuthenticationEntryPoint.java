package br.com.pluviffar.api.auth.security;

import br.com.pluviffar.api.shared.exceptions.ApiErrorWriter;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401 em JSON (sem sessão ou sessão expirada) — nunca redireciona para página de login. */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        errorWriter.write(response, ErrorCode.UNAUTHENTICATED);
    }
}
