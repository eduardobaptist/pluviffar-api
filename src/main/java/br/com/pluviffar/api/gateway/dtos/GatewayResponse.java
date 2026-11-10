package br.com.pluviffar.api.gateway.dtos;

import br.com.pluviffar.api.gateway.entities.Gateway;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Gateway (ESP8266 que recebe dos pluviômetros e publica no broker MQTT).")
public record GatewayResponse(
        @Schema(description = "ID do gateway na plataforma.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Chip ID (segmento do tópico MQTT pluviffar/{hardwareId}/...). Imutável.", example = "gt-570db4",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String hardwareId,
        @Schema(description = "Nome.", example = "Gateway da sede", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Descrição livre.", example = "Instalado no galpão principal")
        String description,
        @Schema(description = "Produtor dono.", requiredMode = Schema.RequiredMode.REQUIRED)
        OwnerSummary owner,
        @Schema(description = "Versão de firmware reportada pelo dispositivo (tópico conf).", example = "1.0.0")
        String fwVersion,
        @Schema(description = "Versão de hardware reportada pelo dispositivo (tópico conf).", example = "1.0")
        String hwVersion,
        @Schema(description = "Quando o dispositivo reportou a configuração pela última vez.", example = "2026-11-10T12:00:00Z")
        Instant configReportedAt,
        @Schema(description = "Criação.", example = "2026-11-10T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Última alteração.", example = "2026-11-10T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(description = "Exclusão (soft delete); preenchido só em listagens do ADMIN com includeDeleted=true.")
        Instant deletedAt
) {
    public static GatewayResponse from(Gateway g) {
        return new GatewayResponse(g.getId(), g.getHardwareId(), g.getName(), g.getDescription(),
                OwnerSummary.from(g.getOwner()), g.getFwVersion(), g.getHwVersion(), g.getConfigReportedAt(),
                g.getCreatedAt(), g.getUpdatedAt(), g.getDeletedAt());
    }
}
