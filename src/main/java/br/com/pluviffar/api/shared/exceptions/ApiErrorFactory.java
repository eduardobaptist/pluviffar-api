package br.com.pluviffar.api.shared.exceptions;

import br.com.pluviffar.api.shared.web.TraceIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Monta o {@link ApiErrorDTO}: resolve a mensagem pt-BR e anexa o traceId da requisição. */
@Component
@RequiredArgsConstructor
public class ApiErrorFactory {

    private final MessageSource messageSource;

    public ApiErrorDTO create(ErrorCode code, Object... args) {
        return create(code, List.of(), args);
    }

    public ApiErrorDTO create(ErrorCode code, List<FieldErrorDTO> errors, Object... args) {
        String message = messageSource.getMessage(code.getMessageKey(), args, code.name(), LocaleContextHolder.getLocale());
        return new ApiErrorDTO(code.name(), message, errors, TraceIdFilter.currentTraceId());
    }
}
