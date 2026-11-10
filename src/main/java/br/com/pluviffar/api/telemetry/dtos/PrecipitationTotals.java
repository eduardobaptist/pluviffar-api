package br.com.pluviffar.api.telemetry.dtos;

import br.com.pluviffar.api.telemetry.repositories.TelemetryQueries.PrecipitationTotalsRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Chuva acumulada em janelas até agora (mm).")
public record PrecipitationTotals(
        @Schema(description = "Desde a meia-noite de hoje, no fuso pedido.", example = "3.20", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal todayMm,
        @Schema(description = "Últimas 24 horas.", example = "5.60", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal last24hMm,
        @Schema(description = "Últimos 7 dias.", example = "21.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal last7dMm,
        @Schema(description = "Últimos 30 dias.", example = "87.20", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal last30dMm
) {
    public static PrecipitationTotals from(PrecipitationTotalsRow row) {
        return new PrecipitationTotals(row.todayMm(), row.last24hMm(), row.last7dMm(), row.last30dMm());
    }
}
