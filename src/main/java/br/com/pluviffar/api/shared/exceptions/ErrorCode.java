package br.com.pluviffar.api.shared.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Catálogo de erros da API. O nome do enum vai no campo "code" do {@link ApiErrorDTO} (contrato estável
 * para o frontend); a mensagem ao usuário (pt-BR) vem de messages.properties pela messageKey.
 */
@Getter
public enum ErrorCode {

    // 400
    VALIDATION_ERROR("error.validation", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("error.malformed-request", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("error.invalid-parameter", HttpStatus.BAD_REQUEST),
    CURRENT_PASSWORD_INCORRECT("error.current-password-incorrect", HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_SAME_AS_CURRENT("error.new-password-same-as-current", HttpStatus.BAD_REQUEST),
    OWNER_REQUIRED("error.owner-required", HttpStatus.BAD_REQUEST),
    INVALID_OWNER("error.invalid-owner", HttpStatus.BAD_REQUEST),

    // 401
    UNAUTHENTICATED("error.unauthenticated", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("error.invalid-credentials", HttpStatus.UNAUTHORIZED),

    // 403
    ACCESS_DENIED("error.access-denied", HttpStatus.FORBIDDEN),
    CSRF_TOKEN_INVALID("error.csrf-token-invalid", HttpStatus.FORBIDDEN),
    PASSWORD_CHANGE_REQUIRED("error.password-change-required", HttpStatus.FORBIDDEN),

    // 404
    RESOURCE_NOT_FOUND("error.resource-not-found", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("error.user-not-found", HttpStatus.NOT_FOUND),
    GATEWAY_NOT_FOUND("error.gateway-not-found", HttpStatus.NOT_FOUND),
    STATION_NOT_FOUND("error.station-not-found", HttpStatus.NOT_FOUND),

    // 405 / 415
    METHOD_NOT_ALLOWED("error.method-not-allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE("error.unsupported-media-type", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // 409
    EMAIL_ALREADY_IN_USE("error.email-already-in-use", HttpStatus.CONFLICT),
    CANNOT_CHANGE_OWN_ROLE("error.cannot-change-own-role", HttpStatus.CONFLICT),
    CANNOT_DEACTIVATE_SELF("error.cannot-deactivate-self", HttpStatus.CONFLICT),
    HARDWARE_ID_ALREADY_REGISTERED("error.hardware-id-already-registered", HttpStatus.CONFLICT),
    GATEWAY_HAS_ACTIVE_STATIONS("error.gateway-has-active-stations", HttpStatus.CONFLICT),
    DATA_CONFLICT("error.data-conflict", HttpStatus.CONFLICT),

    // 500
    INTERNAL_ERROR("error.internal", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String messageKey;
    private final HttpStatus status;

    ErrorCode(String messageKey, HttpStatus status) {
        this.messageKey = messageKey;
        this.status = status;
    }
}
