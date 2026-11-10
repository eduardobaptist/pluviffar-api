package br.com.pluviffar.api.station;

import br.com.pluviffar.api.support.ApiClient;
import br.com.pluviffar.api.support.ApiClient.Response;
import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** CRUD de gateways/estações com checagem de posse (PRODUCER só o que é dele; outro produtor → 404). */
class GatewayStationOwnershipTest extends IntegrationTest {

    private ApiClient admin;
    private ApiClient producerA;
    private ApiClient producerB;
    private String producerAId;
    private String producerBId;

    @BeforeEach
    void setUp() {
        admin = adminClient();
        String emailA = uniqueEmail("produtor-a");
        String emailB = uniqueEmail("produtor-b");
        producerAId = createUser(admin, emailA, "PRODUCER", "provisoria-123");
        producerBId = createUser(admin, emailB, "PRODUCER", "provisoria-123");
        producerA = loginReady(emailA, "provisoria-123", "senha-produtor-a");
        producerB = loginReady(emailB, "provisoria-123", "senha-produtor-b");
    }

    @Test
    void producerManagesOwnGatewayAndStation() {
        String gwHw = chip("gt-");
        Response gw = producerA.post("/api/gateways", Map.of("hardwareId", gwHw.toUpperCase(), "name", "Sede"));
        assertThat(gw.status()).as(gw.body()).isEqualTo(201);
        assertThat(gw.json().path("hardwareId").asString()).isEqualTo(gwHw);
        assertThat(gw.json().path("owner").path("id").asString()).isEqualTo(producerAId);
        String gatewayId = gw.json().path("id").asString();

        Response st = producerA.post("/api/stations", station(chip(""), gatewayId, -29.68, -53.80, true));
        assertThat(st.status()).as(st.body()).isEqualTo(201);
        assertThat(st.json().path("owner").path("id").asString()).isEqualTo(producerAId);
        assertThat(st.json().path("gateway").path("hardwareId").asString()).isEqualTo(gwHw);
        assertThat(st.json().path("isPublic").asBoolean()).isTrue();
        String stationId = st.json().path("id").asString();

        assertThat(producerA.get("/api/gateways").json().path("totalElements").asLong()).isEqualTo(1);
        assertThat(producerA.get("/api/stations?gatewayId=" + gatewayId).json().path("totalElements").asLong()).isEqualTo(1);

        Response updated = producerA.put("/api/stations/" + stationId, station(null, gatewayId, null, null, false));
        assertThat(updated.status()).as(updated.body()).isEqualTo(200);
        assertThat(updated.json().path("latitude").isNull()).isTrue();

        assertThat(producerA.send("DELETE", "/api/stations/" + stationId, null).status()).isEqualTo(204);
        assertThat(producerA.get("/api/stations/" + stationId).status()).isEqualTo(404);
        assertThat(producerA.send("DELETE", "/api/gateways/" + gatewayId, null).status()).isEqualTo(204);
        assertThat(producerA.get("/api/gateways/" + gatewayId).errorCode()).isEqualTo("GATEWAY_NOT_FOUND");
    }

    @Test
    void otherProducerGets404AndNeverSeesForeignDevices() {
        String gatewayId = createGateway(producerA, null);
        String stationId = createStation(producerA, gatewayId);

        for (Response r : new Response[]{
                producerB.get("/api/gateways/" + gatewayId),
                producerB.put("/api/gateways/" + gatewayId, Map.of("name", "Roubado")),
                producerB.send("DELETE", "/api/gateways/" + gatewayId, null),
                producerB.post("/api/stations", station(chip(""), gatewayId, null, null, false))}) {
            assertThat(r.status()).isEqualTo(404);
            assertThat(r.errorCode()).isEqualTo("GATEWAY_NOT_FOUND");
        }
        for (Response r : new Response[]{
                producerB.get("/api/stations/" + stationId),
                producerB.put("/api/stations/" + stationId, station(null, gatewayId, null, null, false)),
                producerB.send("DELETE", "/api/stations/" + stationId, null)}) {
            assertThat(r.status()).isEqualTo(404);
            assertThat(r.errorCode()).isEqualTo("STATION_NOT_FOUND");
        }

        // filtro ownerId é só para ADMIN: o PRODUCER continua restrito aos próprios
        assertThat(producerB.get("/api/gateways?ownerId=" + producerAId).json().path("totalElements").asLong()).isZero();
        assertThat(producerB.get("/api/stations?gatewayId=" + gatewayId).json().path("totalElements").asLong()).isZero();
        // e o dado do produtor A segue intacto
        assertThat(producerA.get("/api/gateways/" + gatewayId).json().path("name").asString()).isEqualTo("Gateway");
    }

    @Test
    void producerCannotAssignOrTransferToAnotherOwner() {
        Response create = producerB.post("/api/gateways",
                Map.of("hardwareId", chip("gt-"), "name", "X", "ownerId", producerAId));
        assertThat(create.status()).isEqualTo(403);
        assertThat(create.errorCode()).isEqualTo("ACCESS_DENIED");

        String gatewayId = createGateway(producerB, null);
        Response transfer = producerB.put("/api/gateways/" + gatewayId, Map.of("name", "X", "ownerId", producerAId));
        assertThat(transfer.status()).isEqualTo(403);

        Response ownId = producerB.put("/api/gateways/" + gatewayId, Map.of("name", "Renomeado", "ownerId", producerBId));
        assertThat(ownId.status()).as("informar o próprio id é permitido").isEqualTo(200);
    }

    @Test
    void producerCannotMoveStationToForeignGateway() {
        String gatewayA = createGateway(producerA, null);
        String gatewayA2 = createGateway(producerA, null);
        String gatewayB = createGateway(producerB, null);
        String stationId = createStation(producerA, gatewayA);

        Response foreign = producerA.put("/api/stations/" + stationId, station(null, gatewayB, null, null, false));
        assertThat(foreign.status()).isEqualTo(404);
        assertThat(foreign.errorCode()).isEqualTo("GATEWAY_NOT_FOUND");

        Response own = producerA.put("/api/stations/" + stationId, station(null, gatewayA2, null, null, false));
        assertThat(own.status()).isEqualTo(200);
        assertThat(own.json().path("gateway").path("id").asString()).isEqualTo(gatewayA2);
    }

    @Test
    void adminChoosesOwnerAndTransferMovesStations() {
        Response noOwner = admin.post("/api/gateways", Map.of("hardwareId", chip("gt-"), "name", "X"));
        assertThat(noOwner.status()).isEqualTo(400);
        assertThat(noOwner.errorCode()).isEqualTo("OWNER_REQUIRED");

        String adminId = admin.get("/api/auth/me").json().path("id").asString();
        Response adminAsOwner = admin.post("/api/gateways", Map.of("hardwareId", chip("gt-"), "name", "X", "ownerId", adminId));
        assertThat(adminAsOwner.status()).isEqualTo(400);
        assertThat(adminAsOwner.errorCode()).isEqualTo("INVALID_OWNER");

        String gatewayId = createGateway(admin, producerAId);
        String stationId = createStation(admin, gatewayId);
        assertThat(producerA.get("/api/stations/" + stationId).status()).isEqualTo(200);
        assertThat(admin.get("/api/gateways?ownerId=" + producerAId).json().path("totalElements").asLong()).isEqualTo(1);

        Response transfer = admin.put("/api/gateways/" + gatewayId, Map.of("name", "Transferido", "ownerId", producerBId));
        assertThat(transfer.status()).as(transfer.body()).isEqualTo(200);
        assertThat(transfer.json().path("owner").path("id").asString()).isEqualTo(producerBId);

        assertThat(producerA.get("/api/stations/" + stationId).status()).isEqualTo(404);
        Response nowB = producerB.get("/api/stations/" + stationId);
        assertThat(nowB.status()).isEqualTo(200);
        assertThat(nowB.json().path("owner").path("id").asString()).as("estação acompanha o gateway").isEqualTo(producerBId);
    }

    @Test
    void hardwareIdIsUniqueAmongActiveAndReleasedAfterDelete() {
        String hw = chip("gt-");
        String gatewayId = createGatewayWithHardwareId(producerA, hw);

        Response duplicate = producerB.post("/api/gateways", Map.of("hardwareId", hw, "name", "Dup"));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.errorCode()).isEqualTo("HARDWARE_ID_ALREADY_REGISTERED");

        Response invalid = producerB.post("/api/gateways", Map.of("hardwareId", "gt_570 db4!", "name", "X"));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.json().path("errors").get(0).path("field").asString()).isEqualTo("hardwareId");

        assertThat(producerA.send("DELETE", "/api/gateways/" + gatewayId, null).status()).isEqualTo(204);
        Response reRegistered = producerB.post("/api/gateways", Map.of("hardwareId", hw, "name", "Comprado"));
        assertThat(reRegistered.status()).as("chip liberado após exclusão").isEqualTo(201);

        Response withDeleted = admin.get("/api/gateways?search=" + hw + "&includeDeleted=true");
        assertThat(withDeleted.json().path("totalElements").asLong()).isEqualTo(2);
        assertThat(admin.get("/api/gateways?search=" + hw).json().path("totalElements").asLong()).isEqualTo(1);
        assertThat(admin.get("/api/gateways/" + gatewayId).json().path("deletedAt").isNull())
                .as("ADMIN vê o excluído no detalhe").isFalse();
        assertThat(producerA.get("/api/gateways?includeDeleted=true").json().path("totalElements").asLong())
                .as("includeDeleted é só para ADMIN").isZero();
    }

    @Test
    void gatewayWithActiveStationsCannotBeDeleted() {
        String gatewayId = createGateway(producerA, null);
        String stationId = createStation(producerA, gatewayId);

        Response blocked = producerA.send("DELETE", "/api/gateways/" + gatewayId, null);
        assertThat(blocked.status()).isEqualTo(409);
        assertThat(blocked.errorCode()).isEqualTo("GATEWAY_HAS_ACTIVE_STATIONS");

        assertThat(producerA.send("DELETE", "/api/stations/" + stationId, null).status()).isEqualTo(204);
        assertThat(producerA.send("DELETE", "/api/gateways/" + gatewayId, null).status()).isEqualTo(204);
        assertThat(admin.get("/api/stations/" + stationId).json().path("isPublic").asBoolean())
                .as("excluída sai da área pública").isFalse();
    }

    @Test
    void stationLocationRules() {
        String gatewayId = createGateway(producerA, null);

        Response partial = producerA.post("/api/stations", station(chip(""), gatewayId, -29.6, null, false));
        assertThat(partial.status()).isEqualTo(400);
        assertThat(fields(partial)).contains("locationComplete");

        Response publicNoLocation = producerA.post("/api/stations", station(chip(""), gatewayId, null, null, true));
        assertThat(publicNoLocation.status()).isEqualTo(400);
        assertThat(fields(publicNoLocation)).contains("publicWithLocation");

        Response outOfRange = producerA.post("/api/stations", station(chip(""), gatewayId, 100.0, 0.0, false));
        assertThat(outOfRange.status()).isEqualTo(400);
        assertThat(fields(outOfRange)).contains("latitude");
    }

    // --- helpers ---

    /** Chip ID aleatório (o banco é compartilhado entre os testes). */
    private static String chip(String prefix) {
        byte[] bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        return prefix + HexFormat.of().formatHex(bytes);
    }

    private String createGateway(ApiClient client, String ownerId) {
        return createGatewayWithHardwareId(client, chip("gt-"), ownerId);
    }

    private String createGatewayWithHardwareId(ApiClient client, String hardwareId) {
        return createGatewayWithHardwareId(client, hardwareId, null);
    }

    private String createGatewayWithHardwareId(ApiClient client, String hardwareId, String ownerId) {
        Map<String, Object> body = new HashMap<>(Map.of("hardwareId", hardwareId, "name", "Gateway"));
        if (ownerId != null) {
            body.put("ownerId", ownerId);
        }
        Response r = client.post("/api/gateways", body);
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().path("id").asString();
    }

    private String createStation(ApiClient client, String gatewayId) {
        Response r = client.post("/api/stations", station(chip(""), gatewayId, null, null, false));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().path("id").asString();
    }

    /** Corpo de estação; hardwareId null = corpo de edição (sem chip ID). */
    private static Map<String, Object> station(String hardwareId, String gatewayId, Double lat, Double lon, boolean isPublic) {
        Map<String, Object> body = new HashMap<>();
        if (hardwareId != null) {
            body.put("hardwareId", hardwareId);
        }
        body.put("gatewayId", gatewayId);
        body.put("name", "Pluviômetro");
        body.put("latitude", lat);
        body.put("longitude", lon);
        body.put("isPublic", isPublic);
        return body;
    }

    private static java.util.List<String> fields(Response r) {
        return r.json().path("errors").valueStream().map(e -> e.path("field").asString()).toList();
    }
}
