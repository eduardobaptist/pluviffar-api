package br.com.pluviffar.api.shared.exceptions;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escreve um {@link ApiErrorDTO} direto na resposta. Usado fora do Spring MVC (filtros do Spring Security:
 * 401/403/CSRF), onde o @RestControllerAdvice não alcança.
 */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

    private final ApiErrorFactory errorFactory;
    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), errorFactory.create(code));
    }
}
