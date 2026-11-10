package br.com.pluviffar.api.telemetry.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Resumo da estação para o painel: última leitura, último status e totais de chuva.")
public record StationOverviewResponse(
        @Schema(description = "ID da estação.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID stationId,
        @Schema(description = "Fuso usado para \"hoje\".", example = "America/Sao_Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
        @Schema(description = "Última leitura recebida (null se nunca enviou).")
        ReadingResponse lastReading,
        @Schema(description = "Último status recebido (null se nunca enviou).")
        DeviceStatusResponse lastStatus,
        @Schema(description = "Totais de chuva.", requiredMode = Schema.RequiredMode.REQUIRED)
        PrecipitationTotals precipitation
) {
}
