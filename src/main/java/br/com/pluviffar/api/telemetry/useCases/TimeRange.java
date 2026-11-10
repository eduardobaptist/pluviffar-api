package br.com.pluviffar.api.telemetry.useCases;

import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Período [from, to) de uma consulta de telemetria, com padrões e limite de tamanho. */
public record TimeRange(Instant from, Instant to) {

    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    /**
     * to ausente = agora; from ausente = to − defaultLookback. Recusa from ≥ to e períodos maiores que maxRange.
     */
    public static TimeRange resolve(Instant from, Instant to, Duration defaultLookback, Duration maxRange) {
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(defaultLookback);
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        if (Duration.between(start, end).compareTo(maxRange) > 0) {
            throw new BusinessException(ErrorCode.PERIOD_TOO_LONG, maxRange.toDays());
        }
        return new TimeRange(start, end);
    }

    /** Fuso IANA (ex.: America/Sao_Paulo); inválido → 400 INVALID_PARAMETER. */
    public static ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }
    }
}
