package br.com.pluviffar.api.station;

import br.com.pluviffar.api.support.ApiClient;
import br.com.pluviffar.api.support.ApiClient.Response;
import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Estação favorita: uma por usuário, só entre as que ele enxerga; exposta no campo "favorite" das estações. */
class FavoriteStationTest extends IntegrationTest {

    private ApiClient admin;
    private ApiClient producerA;
    private ApiClient producerB;
    private String producerBId;

    @BeforeEach
    void setUp() {
        admin = adminClient();
        String emailA = uniqueEmail("fav-a");
        String emailB = uniqueEmail("fav-b");
        createUser(admin, emailA, "PRODUCER", "provisoria-123");
        producerBId = createUser(admin, emailB, "PRODUCER", "provisoria-123");
        producerA = loginReady(emailA, "provisoria-123", "senha-produtor-a");
        producerB = loginReady(emailB, "provisoria-123", "senha-produtor-b");
    }

    @Test
    void producerMarksOneFavoriteVisibleInStationList() {
        String gatewayId = createGateway(producerA);
        String first = createStation(producerA, gatewayId);
        String second = createStation(producerA, gatewayId);
        assertThat(favorites(producerA)).isEmpty();

        assertThat(producerA.put("/api/stations/" + first + "/favorite", null).status()).isEqualTo(204);
        assertThat(producerA.put("/api/stations/" + first + "/favorite", null).status()).as("idempotente").isEqualTo(204);
        assertThat(favorites(producerA)).containsExactly(first);
        assertThat(producerA.get("/api/stations/" + first).json().path("favorite").asBoolean()).isTrue();

        // marcar outra substitui a anterior (uma por usuário)
        assertThat(producerA.put("/api/stations/" + second + "/favorite", null).status()).isEqualTo(204);
        assertThat(favorites(producerA)).containsExactly(second);

        // desmarcar outra estação não mexe na favorita; desmarcar a favorita limpa
        assertThat(producerA.send("DELETE", "/api/stations/" + first + "/favorite", null).status()).isEqualTo(204);
        assertThat(favorites(producerA)).containsExactly(second);
        assertThat(producerA.send("DELETE", "/api/stations/" + second + "/favorite", null).status()).isEqualTo(204);
        assertThat(favorites(producerA)).isEmpty();
    }

    @Test
    void producerCannotFavoriteForeignStation() {
        String stationOfB = createStation(producerB, createGateway(producerB));

        Response r = producerA.put("/api/stations/" + stationOfB + "/favorite", null);
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.errorCode()).isEqualTo("STATION_NOT_FOUND");
        assertThat(producerB.get("/api/stations/" + stationOfB).json().path("favorite").asBoolean()).isFalse();
    }

    @Test
    void favoriteDoesNotFollowTransferredStation() {
        String gatewayId = createGateway(producerA);
        String stationId = createStation(producerA, gatewayId);
        assertThat(producerA.put("/api/stations/" + stationId + "/favorite", null).status()).isEqualTo(204);

        Response transfer = admin.put("/api/gateways/" + gatewayId, Map.of("name", "Transferido", "ownerId", producerBId));
        assertThat(transfer.status()).as(transfer.body()).isEqualTo(200);

        assertThat(favorites(producerA)).isEmpty();
        assertThat(favorites(producerB)).as("o favorito é de cada usuário, não acompanha a estação").isEmpty();
    }

    @Test
    void adminFavoriteIsIndependentFromProducer() {
        String stationId = createStation(producerA, createGateway(producerA));
        assertThat(admin.put("/api/stations/" + stationId + "/favorite", null).status()).isEqualTo(204);

        assertThat(admin.get("/api/stations/" + stationId).json().path("favorite").asBoolean()).isTrue();
        assertThat(producerA.get("/api/stations/" + stationId).json().path("favorite").asBoolean()).isFalse();
    }

    // --- helpers ---

    /** IDs marcados como favorite=true na listagem do usuário. */
    private static List<String> favorites(ApiClient client) {
        Response list = client.get("/api/stations?size=100");
        assertThat(list.status()).as(list.body()).isEqualTo(200);
        return list.json().path("content").valueStream()
                .filter(s -> s.path("favorite").asBoolean())
                .map(s -> s.path("id").asString()).toList();
    }

    private static String chip(String prefix) {
        byte[] bytes = new byte[4];
        ThreadLocalRandom.current().nextBytes(bytes);
        return prefix + HexFormat.of().formatHex(bytes);
    }

    private String createGateway(ApiClient client) {
        Response r = client.post("/api/gateways", Map.of("hardwareId", chip("gt-"), "name", "Gateway"));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().path("id").asString();
    }

    private String createStation(ApiClient client, String gatewayId) {
        Response r = client.post("/api/stations",
                Map.of("hardwareId", chip(""), "gatewayId", gatewayId, "name", "Pluviômetro", "isPublic", false));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().path("id").asString();
    }
}
