package br.com.pluviffar.api.telemetry.dtos;

import br.com.pluviffar.api.telemetry.entities.ConfigReport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Configuração reportada pelo dispositivo (tópico conf). Registrada só quando algo muda.")
public record ConfigReportResponse(
        @Schema(description = "Quando foi recebida.", example = "2026-11-10T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant reportedAt,
        @Schema(description = "Gateway pelo qual chegou.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID gatewayId,
        @Schema(description = "Firmware do gateway.", example = "1.0.0") String fwGateway,
        @Schema(description = "Hardware do gateway.", example = "1.0") String hwGateway,
        @Schema(description = "Firmware do pluviômetro.", example = "1.0.0") String fwStation,
        @Schema(description = "Hardware do pluviômetro.", example = "1.0") String hwStation,
        @Schema(description = "Intervalo de envio (s).", example = "60") Integer sendIntervalS,
        @Schema(description = "Milímetros por basculada.", example = "0.200") BigDecimal mmPerTip
) {
    public static ConfigReportResponse from(ConfigReport c) {
        return new ConfigReportResponse(c.getReportedAt(), c.getGatewayId(), c.getFwGateway(), c.getHwGateway(),
                c.getFwStation(), c.getHwStation(), c.getSendIntervalS(), c.getMmPerTip());
    }
}
