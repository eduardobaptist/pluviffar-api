package br.com.pluviffar.api.telemetry.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Um ponto da série: chuva acumulada no bucket.")
public record PrecipitationPoint(
        @Schema(description = "Início do bucket (ISO-8601, UTC), alinhado ao fuso pedido.", example = "2026-11-10T03:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant bucketStart,
        @Schema(description = "Chuva no bucket (mm).", example = "12.40", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal precipitationMm,
        @Schema(description = "Leituras recebidas no bucket (ajuda a perceber falhas de envio).", example = "1440",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long readingsCount
) {
}
