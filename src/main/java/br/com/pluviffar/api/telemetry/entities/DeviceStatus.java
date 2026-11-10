package br.com.pluviffar.api.telemetry.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Status do dispositivo (tópico status) — hypertable, escrita só pelo worker; somente leitura na API. */
@Entity
@Immutable
@Table(name = "device_status", schema = "telemetry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceStatus {

    @EmbeddedId
    private TelemetryId id;

    /** Gateway pelo qual o pacote chegou. */
    @Column(name = "gateway_id", nullable = false)
    private UUID gatewayId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "battery_v")
    private Float batteryV;

    @Column(name = "battery_pct")
    private Short batteryPct;

    @Column(name = "rssi_gateway_dbm")
    private Short rssiGatewayDbm;

    @Column(name = "rssi_station_dbm")
    private Short rssiStationDbm;

    @Column(name = "uptime_gateway_s")
    private Long uptimeGatewayS;

    @Column(name = "uptime_station_s")
    private Long uptimeStationS;
}
