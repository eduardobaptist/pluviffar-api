package br.com.pluviffar.api.station.dtos;

import br.com.pluviffar.api.gateway.dtos.OwnerSummary;
import br.com.pluviffar.api.station.entities.Station;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Estação (pluviômetro), na visão do dono/ADMIN. A área pública usa outro DTO, sem dados do dono.")
public record StationResponse(
        @Schema(description = "ID da estação na plataforma.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Chip ID do pluviômetro (campo id do payload MQTT). Imutável.", example = "580db2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String hardwareId,
        @Schema(description = "Nome.", example = "Pluviômetro do talhão 3", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Descrição livre.", example = "Ao lado da porteira")
        String description,
        @Schema(description = "Latitude (graus decimais, WGS84).", example = "-29.6842")
        Double latitude,
        @Schema(description = "Longitude (graus decimais, WGS84).", example = "-53.8069")
        Double longitude,
        @Schema(description = "Se aparece na área pública (só chuva e localização, nunca dados do dono).", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isPublic,
        @Schema(description = "Gateway vinculado.", requiredMode = Schema.RequiredMode.REQUIRED)
        GatewaySummary gateway,
        @Schema(description = "Produtor dono (sempre o dono do gateway).", requiredMode = Schema.RequiredMode.REQUIRED)
        OwnerSummary owner,
        @Schema(description = "Firmware reportado pelo pluviômetro (tópico conf).", example = "1.0.0")
        String fwVersion,
        @Schema(description = "Hardware reportado pelo pluviômetro (tópico conf).", example = "1.0")
        String hwVersion,
        @Schema(description = "Intervalo de envio reportado, em segundos.", example = "60")
        Integer sendIntervalS,
        @Schema(description = "Milímetros por basculada reportados.", example = "0.200")
        BigDecimal mmPerTip,
        @Schema(description = "Quando o dispositivo reportou a configuração pela última vez.", example = "2026-11-10T12:00:00Z")
        Instant configReportedAt,
        @Schema(description = "Criação.", example = "2026-11-10T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Última alteração.", example = "2026-11-10T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,
        @Schema(description = "Exclusão (soft delete); preenchido só para o ADMIN com includeDeleted=true.")
        Instant deletedAt
) {
    public static StationResponse from(Station s) {
        return new StationResponse(s.getId(), s.getHardwareId(), s.getName(), s.getDescription(), s.getLatitude(),
                s.getLongitude(), s.isPublic(), GatewaySummary.from(s.getGateway()), OwnerSummary.from(s.getOwner()),
                s.getFwVersion(), s.getHwVersion(), s.getSendIntervalS(), s.getMmPerTip(), s.getConfigReportedAt(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getDeletedAt());
    }
}
