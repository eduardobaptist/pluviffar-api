package br.com.pluviffar.api.telemetry;

import br.com.pluviffar.api.support.ApiClient;
import br.com.pluviffar.api.support.ApiClient.Response;
import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consulta de telemetria (área logada, com posse) e área pública. A telemetria é inserida direto no banco,
 * simulando o worker.
 */
class TelemetryAndPublicAreaTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private ApiClient admin;
    private ApiClient owner;
    private ApiClient otherProducer;
    private String gatewayId;
    private String publicStationId;
    private String privateStationId;
    private Instant now;

    @BeforeEach
    void setUp() {
        admin = adminClient();
        String ownerEmail = uniqueEmail("dono");
        String otherEmail = uniqueEmail("outro");
        createUser(admin, ownerEmail, "PRODUCER", "provisoria-123");
        createUser(admin, otherEmail, "PRODUCER", "provisoria-123");
        owner = loginReady(ownerEmail, "provisoria-123", "senha-do-dono");
        otherProducer = loginReady(otherEmail, "provisoria-123", "senha-do-outro");

        gatewayId = owner.post("/api/gateways", Map.of("hardwareId", chip("gt-"), "name", "Sede")).json().path("id").asString();
        publicStationId = createStation(-29.6842, -53.8069, true);
        privateStationId = createStation(null, null, false);

        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        // recentes: 24 h = 0.60; 7 dias = 1.60; 30 dias = 3.60
        insertReading(publicStationId, now.minus(10, ChronoUnit.MINUTES), "0.20", 1, 10);
        insertReading(publicStationId, now.minus(2, ChronoUnit.HOURS), "0.40", 2, 9);
        insertReading(publicStationId, now.minus(3, ChronoUnit.DAYS), "1.00", 5, 8);
        insertReading(publicStationId, now.minus(20, ChronoUnit.DAYS), "2.00", 10, 7);
        // históricas, em volta da meia-noite de São Paulo (UTC-3) de 14/06/2025
        insertReading(publicStationId, Instant.parse("2025-06-14T02:30:00Z"), "0.40", 2, 1); // 13/06 23:30 local
        insertReading(publicStationId, Instant.parse("2025-06-14T03:30:00Z"), "0.60", 3, 2); // 14/06 00:30 local
        insertReading(publicStationId, Instant.parse("2025-06-14T04:00:00Z"), "1.00", 5, 3); // 14/06 01:00 local
        insertReading(privateStationId, now.minus(5, ChronoUnit.MINUTES), "9.99", 50, 1);

        jdbc.update("""
                INSERT INTO telemetry.device_status (station_id, gateway_id, measured_at, battery_v, battery_pct,
                       rssi_gateway_dbm, rssi_station_dbm, uptime_gateway_s, uptime_station_s)
                VALUES (?, ?, ?, 6.5, 74, -86, -60, 86432, 85321)""",
                UUID.fromString(publicStationId), UUID.fromString(gatewayId), utc(now.minus(1, ChronoUnit.MINUTES)));
        jdbc.update("""
                INSERT INTO telemetry.config_reports (station_id, gateway_id, fw_gateway, hw_gateway, fw_station, hw_station,
                       send_interval_s, mm_per_tip)
                VALUES (?, ?, '1.0.0', '1.0', '1.0.1', '1.0', 60, 0.200)""",
                UUID.fromString(publicStationId), UUID.fromString(gatewayId));
    }

    @Test
    void dailySeriesIsAlignedToRequestedTimezone() {
        String range = "&from=2025-06-13T00:00:00Z&to=2025-06-15T00:00:00Z";

        JsonNode saoPaulo = owner.get("/api/stations/" + publicStationId + "/precipitation?bucket=DAY" + range).json();
        assertThat(saoPaulo.path("timezone").asString()).isEqualTo("America/Sao_Paulo");
        assertThat(points(saoPaulo)).containsExactly(
                "2025-06-13T03:00:00Z=0.40/1",
                "2025-06-14T03:00:00Z=1.60/2");
        assertThat(saoPaulo.path("totalMm").decimalValue()).isEqualByComparingTo("2.00");

        JsonNode utc = owner.get("/api/stations/" + publicStationId + "/precipitation?bucket=DAY&timezone=UTC" + range).json();
        assertThat(points(utc)).containsExactly("2025-06-14T00:00:00Z=2.00/3");
    }

    @Test
    void hourlySeriesAndOverview() {
        JsonNode hourly = owner.get("/api/stations/" + publicStationId + "/precipitation?bucket=HOUR").json();
        assertThat(hourly.path("points").size()).as("padrão de 7 dias: 3 leituras em horas distintas").isEqualTo(3);
        assertThat(hourly.path("totalMm").decimalValue()).isEqualByComparingTo("1.60");

        Response overview = owner.get("/api/stations/" + publicStationId + "/overview");
        assertThat(overview.status()).isEqualTo(200);
        JsonNode body = overview.json();
        assertThat(body.path("lastReading").path("precipitationMm").decimalValue()).isEqualByComparingTo("0.20");
        assertThat(body.path("lastStatus").path("batteryPct").asInt()).isEqualTo(74);
        assertThat(body.path("lastStatus").path("gatewayId").asString()).isEqualTo(gatewayId);
        JsonNode totals = body.path("precipitation");
        assertThat(totals.path("last24hMm").decimalValue()).isEqualByComparingTo("0.60");
        assertThat(totals.path("last7dMm").decimalValue()).isEqualByComparingTo("1.60");
        assertThat(totals.path("last30dMm").decimalValue()).isEqualByComparingTo("3.60");
        assertThat(totals.path("todayMm").decimalValue()).isLessThanOrEqualTo(new BigDecimal("0.60"));

        JsonNode empty = owner.get("/api/stations/" + createStation(null, null, false) + "/overview").json();
        assertThat(empty.path("lastReading").isNull()).isTrue();
        assertThat(empty.path("precipitation").path("last30dMm").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void rawReadingsStatusAndConfigAreNewestFirstAndPaginated() {
        Response readings = owner.get("/api/stations/" + publicStationId + "/readings?size=1");
        assertThat(readings.status()).isEqualTo(200);
        assertThat(readings.json().path("totalElements").asLong()).as("padrão: últimas 24 h").isEqualTo(2);
        assertThat(readings.json().path("content").get(0).path("seq").asLong()).isEqualTo(10);

        Response all = owner.get("/api/stations/" + publicStationId + "/readings?from=2025-06-01T00:00:00Z&to=2025-07-01T00:00:00Z");
        assertThat(all.json().path("content").valueStream().map(r -> r.path("seq").asLong()).toList())
                .containsExactly(3L, 2L, 1L);

        Response status = owner.get("/api/stations/" + publicStationId + "/status");
        assertThat(status.json().path("content").get(0).path("rssiStationDbm").asInt()).isEqualTo(-60);

        Response config = owner.get("/api/stations/" + publicStationId + "/config-reports");
        assertThat(config.json().path("content").get(0).path("fwStation").asString()).isEqualTo("1.0.1");
        assertThat(config.json().path("content").get(0).path("mmPerTip").decimalValue()).isEqualByComparingTo("0.2");
    }

    @Test
    void invalidPeriodsAndParametersReturn400() {
        String base = "/api/stations/" + publicStationId;

        Response inverted = owner.get(base + "/readings?from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z");
        assertThat(inverted.errorCode()).isEqualTo("INVALID_PERIOD");

        Response tooLong = owner.get(base + "/precipitation?bucket=HOUR&from=2026-01-01T00:00:00Z&to=2026-06-01T00:00:00Z");
        assertThat(tooLong.status()).isEqualTo(400);
        assertThat(tooLong.errorCode()).isEqualTo("PERIOD_TOO_LONG");
        assertThat(tooLong.json().path("message").asString()).contains("93 dias");

        assertThat(owner.get(base + "/overview?timezone=Marte/Olimpo").errorCode()).isEqualTo("INVALID_PARAMETER");
        assertThat(owner.get(base + "/precipitation?bucket=YEAR").errorCode()).isEqualTo("INVALID_PARAMETER");
        assertThat(owner.get(base + "/readings?from=ontem").errorCode()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void telemetryFollowsStationOwnership() {
        for (String path : List.of("/overview", "/readings", "/status", "/config-reports", "/precipitation")) {
            Response r = otherProducer.get("/api/stations/" + publicStationId + path);
            assertThat(r.status()).as(path).isEqualTo(404);
            assertThat(r.errorCode()).isEqualTo("STATION_NOT_FOUND");
        }
        assertThat(newClient().get("/api/stations/" + publicStationId + "/readings").status()).isEqualTo(401);
        assertThat(admin.get("/api/stations/" + publicStationId + "/readings").status()).isEqualTo(200);

        // dado medido não se apaga: após excluir a estação, o dono perde o acesso mas o ADMIN ainda lê
        assertThat(owner.send("DELETE", "/api/stations/" + privateStationId, null).status()).isEqualTo(204);
        assertThat(owner.get("/api/stations/" + privateStationId + "/readings").status()).isEqualTo(404);
        Response adminReads = admin.get("/api/stations/" + privateStationId + "/readings");
        assertThat(adminReads.status()).isEqualTo(200);
        assertThat(adminReads.json().path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void publicAreaShowsOnlyPublicStationsWithoutOwnerData() {
        ApiClient anonymous = newClient();

        Response list = anonymous.get("/api/public/stations");
        assertThat(list.status()).isEqualTo(200);
        JsonNode mine = list.json().valueStream()
                .filter(s -> s.path("id").asString().equals(publicStationId)).findFirst().orElseThrow();
        assertThat(mine.path("last24hMm").decimalValue()).isEqualByComparingTo("0.60");
        assertThat(mine.path("latitude").asDouble()).isEqualTo(-29.6842);
        assertThat(mine.path("lastReadingAt").asString()).isEqualTo(now.minus(10, ChronoUnit.MINUTES).toString());
        assertThat(list.json().valueStream().map(s -> s.path("id").asString())).doesNotContain(privateStationId);
        assertNoPrivateFields(mine);

        Response detail = anonymous.get("/api/public/stations/" + publicStationId);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.json().path("precipitation").path("last7dMm").decimalValue()).isEqualByComparingTo("1.60");
        assertNoPrivateFields(detail.json());

        Response series = anonymous.get("/api/public/stations/" + publicStationId
                + "/precipitation?bucket=DAY&from=2025-06-13T00:00:00Z&to=2025-06-15T00:00:00Z");
        assertThat(points(series.json())).containsExactly("2025-06-13T03:00:00Z=0.40/1", "2025-06-14T03:00:00Z=1.60/2");

        Response privateOne = anonymous.get("/api/public/stations/" + privateStationId);
        assertThat(privateOne.status()).isEqualTo(404);
        assertThat(privateOne.errorCode()).isEqualTo("STATION_NOT_FOUND");
        assertThat(anonymous.get("/api/public/stations/" + privateStationId + "/precipitation").status()).isEqualTo(404);
        assertThat(anonymous.cookie("SESSION")).as("área pública não cria sessão").isNull();

        // o dono torna a estação privada → some da área pública
        Map<String, Object> update = new HashMap<>(Map.of("gatewayId", gatewayId, "name", "Agora privada", "isPublic", false));
        assertThat(owner.put("/api/stations/" + publicStationId, update).status()).isEqualTo(200);
        assertThat(anonymous.get("/api/public/stations/" + publicStationId).status()).isEqualTo(404);
    }

    // --- helpers ---

    private static void assertNoPrivateFields(JsonNode station) {
        assertThat(station.has("owner")).isFalse();
        assertThat(station.has("hardwareId")).isFalse();
        assertThat(station.has("description")).isFalse();
        assertThat(station.has("gateway")).isFalse();
    }

    private static List<String> points(JsonNode series) {
        return series.path("points").valueStream()
                .map(p -> p.path("bucketStart").asString() + "=" + p.path("precipitationMm").decimalValue().setScale(2)
                        + "/" + p.path("readingsCount").asLong())
                .toList();
    }

    private String createStation(Double lat, Double lon, boolean isPublic) {
        Map<String, Object> body = new HashMap<>();
        body.put("hardwareId", chip(""));
        body.put("gatewayId", gatewayId);
        body.put("name", "Estação");
        body.put("latitude", lat);
        body.put("longitude", lon);
        body.put("isPublic", isPublic);
        Response r = owner.post("/api/stations", body);
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().path("id").asString();
    }

    private void insertReading(String stationId, Instant measuredAt, String mm, int tips, long seq) {
        jdbc.update("INSERT INTO telemetry.readings (station_id, measured_at, tips_per_minute, precipitation_mm, seq) "
                + "VALUES (?, ?, ?, ?, ?)", UUID.fromString(stationId), utc(measuredAt), tips, new BigDecimal(mm), seq);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String chip(String prefix) {
        byte[] bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        return prefix + HexFormat.of().formatHex(bytes);
    }
}
