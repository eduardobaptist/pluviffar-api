package br.com.pluviffar.api.publicdata.dtos;

import br.com.pluviffar.api.telemetry.dtos.PrecipitationTotals;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Detalhe de uma estação pública: localização e chuva acumulada. Sem dados do dono e sem chip ID.")
public record PublicStationDetail(
        @Schema(description = "ID da estação.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Nome dado pelo dono.", example = "Pluviômetro do talhão 3", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Latitude (WGS84).", example = "-29.6842", requiredMode = Schema.RequiredMode.REQUIRED)
        double latitude,
        @Schema(description = "Longitude (WGS84).", example = "-53.8069", requiredMode = Schema.RequiredMode.REQUIRED)
        double longitude,
        @Schema(description = "Última leitura recebida (null se nunca enviou).", example = "2026-11-10T12:00:00Z")
        Instant lastReadingAt,
        @Schema(description = "Fuso usado para \"hoje\".", example = "America/Sao_Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
        @Schema(description = "Totais de chuva.", requiredMode = Schema.RequiredMode.REQUIRED)
        PrecipitationTotals precipitation
) {
}
