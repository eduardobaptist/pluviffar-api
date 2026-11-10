package br.com.pluviffar.api.telemetry.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Duration;

/** Granularidade da série de chuva. Período padrão (sem from) e máximo variam para limitar o número de pontos. */
@Getter
@Schema(description = "Granularidade da agregação: HOUR (padrão 7 dias, máx. 93), DAY (padrão 30 dias, máx. 1100), "
        + "WEEK (padrão 365 dias, máx. 3660; semanas começam na segunda), MONTH (padrão 365 dias, máx. 10980).")
public enum PrecipitationBucket {
    HOUR("1 hour", Duration.ofDays(7), Duration.ofDays(93)),
    DAY("1 day", Duration.ofDays(30), Duration.ofDays(1100)),
    WEEK("1 week", Duration.ofDays(365), Duration.ofDays(3660)),
    MONTH("1 month", Duration.ofDays(365), Duration.ofDays(10980));

    /** Intervalo do PostgreSQL usado no time_bucket. */
    private final String sqlInterval;
    private final Duration defaultLookback;
    private final Duration maxRange;

    PrecipitationBucket(String sqlInterval, Duration defaultLookback, Duration maxRange) {
        this.sqlInterval = sqlInterval;
        this.defaultLookback = defaultLookback;
        this.maxRange = maxRange;
    }
}
