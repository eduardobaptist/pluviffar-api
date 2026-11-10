package br.com.pluviffar.api.telemetry.dtos;

import br.com.pluviffar.api.telemetry.entities.Reading;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Leitura de chuva enviada pelo pluviômetro (tópico dados).")
public record ReadingResponse(
        @Schema(description = "Momento da medição (relógio do dispositivo).", example = "2026-11-10T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant measuredAt,
        @Schema(description = "Quando o worker gravou a leitura.", example = "2026-11-10T12:00:02Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant receivedAt,
        @Schema(description = "Basculadas por minuto.", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        int tipsPerMinute,
        @Schema(description = "Chuva no intervalo, em mm.", example = "0.80", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal precipitationMm,
        @Schema(description = "Sequência do dispositivo (lacunas indicam pacotes perdidos; zera no reboot).", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long seq
) {
    public static ReadingResponse from(Reading r) {
        return new ReadingResponse(r.getId().getMeasuredAt(), r.getReceivedAt(), r.getTipsPerMinute(),
                r.getPrecipitationMm(), r.getSeq());
    }
}
