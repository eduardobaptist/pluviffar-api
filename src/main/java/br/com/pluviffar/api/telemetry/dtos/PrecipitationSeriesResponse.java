package br.com.pluviffar.api.telemetry.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Série de chuva agregada. Buckets sem leitura não aparecem (sem dado ≠ chuva zero).")
public record PrecipitationSeriesResponse(
        @Schema(description = "ID da estação.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID stationId,
        @Schema(description = "Granularidade.", example = "DAY", requiredMode = Schema.RequiredMode.REQUIRED)
        PrecipitationBucket bucket,
        @Schema(description = "Fuso usado para alinhar os buckets.", example = "America/Sao_Paulo",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
        @Schema(description = "Início do período consultado (inclusivo).", example = "2026-10-11T03:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant from,
        @Schema(description = "Fim do período consultado (exclusivo).", example = "2026-11-10T03:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant to,
        @Schema(description = "Chuva total no período (mm).", example = "87.20", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal totalMm,
        @Schema(description = "Pontos em ordem cronológica.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PrecipitationPoint> points
) {
}
