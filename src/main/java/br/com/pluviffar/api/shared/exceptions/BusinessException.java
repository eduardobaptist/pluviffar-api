package br.com.pluviffar.api.shared.exceptions;

import lombok.Getter;

/**
 * Erro de regra de negócio. Vira um {@link ApiErrorDTO} com o status HTTP do {@link ErrorCode}.
 * Os args são usados na mensagem ({0}, {1}...) do messages.properties.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.args = args;
    }
}
