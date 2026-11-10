package br.com.pluviffar.api.telemetry.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Histórico do tópico conf (tabela comum, escrita só pelo worker quando algo muda); somente leitura na API. */
@Entity
@Immutable
@Table(name = "config_reports", schema = "telemetry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfigReport {

    @Id
    private Long id;

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "gateway_id", nullable = false)
    private UUID gatewayId;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "fw_gateway", length = 20)
    private String fwGateway;

    @Column(name = "hw_gateway", length = 20)
    private String hwGateway;

    @Column(name = "fw_station", length = 20)
    private String fwStation;

    @Column(name = "hw_station", length = 20)
    private String hwStation;

    @Column(name = "send_interval_s")
    private Integer sendIntervalS;

    @Column(name = "mm_per_tip", precision = 5, scale = 3)
    private BigDecimal mmPerTip;
}
