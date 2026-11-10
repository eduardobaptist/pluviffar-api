package br.com.pluviffar.api.telemetry.dtos;

import br.com.pluviffar.api.telemetry.entities.DeviceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Status do dispositivo (tópico status): bateria, sinal e uptime.")
public record DeviceStatusResponse(
        @Schema(description = "Momento da medição.", example = "2026-11-10T12:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant measuredAt,
        @Schema(description = "Quando o worker gravou.", example = "2026-11-10T12:00:02Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant receivedAt,
        @Schema(description = "Gateway pelo qual o pacote chegou.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID gatewayId,
        @Schema(description = "Tensão da bateria (V).", example = "6.5")
        Float batteryV,
        @Schema(description = "Carga da bateria (%).", example = "74")
        Short batteryPct,
        @Schema(description = "RSSI do Wi-Fi do gateway (dBm).", example = "-86")
        Short rssiGatewayDbm,
        @Schema(description = "RSSI do pluviômetro visto pelo gateway, via ESP-NOW (dBm).", example = "-60")
        Short rssiStationDbm,
        @Schema(description = "Uptime do gateway (s).", example = "86432")
        Long uptimeGatewayS,
        @Schema(description = "Uptime do pluviômetro (s).", example = "85321")
        Long uptimeStationS
) {
    public static DeviceStatusResponse from(DeviceStatus s) {
        return new DeviceStatusResponse(s.getId().getMeasuredAt(), s.getReceivedAt(), s.getGatewayId(), s.getBatteryV(),
                s.getBatteryPct(), s.getRssiGatewayDbm(), s.getRssiStationDbm(), s.getUptimeGatewayS(), s.getUptimeStationS());
    }
}
