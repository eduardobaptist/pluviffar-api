package br.com.pluviffar.api.station.dtos;

import br.com.pluviffar.api.gateway.entities.Gateway;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Gateway ao qual a estação está vinculada.")
public record GatewaySummary(
        @Schema(description = "ID do gateway.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Chip ID do gateway.", example = "gt-570db4", requiredMode = Schema.RequiredMode.REQUIRED)
        String hardwareId,
        @Schema(description = "Nome do gateway.", example = "Gateway da sede", requiredMode = Schema.RequiredMode.REQUIRED)
        String name
) {
    public static GatewaySummary from(Gateway gateway) {
        return new GatewaySummary(gateway.getId(), gateway.getHardwareId(), gateway.getName());
    }
}
