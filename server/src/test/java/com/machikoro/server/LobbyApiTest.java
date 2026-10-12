package com.machikoro.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ScriptedDice.class)
class LobbyApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createReturnsCredentialsAndHostSeat() {
        ResponseEntity<JsonNode> created = post("/api/games", Map.of("name", "Alice"), null);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = created.getBody();
        String gameId = body.path("gameId").asText();
        assertThat(gameId).hasSize(6);
        assertThat(body.path("playerId").asText()).isNotBlank();
        assertThat(body.path("playerToken").asText()).isNotBlank();

        JsonNode game = get("/api/games/" + gameId).getBody();
        assertThat(game.path("status").asText()).isEqualTo("LOBBY");
        assertThat(game.path("hostPlayerId").asText()).isEqualTo(body.path("playerId").asText());
        assertThat(game.path("players")).hasSize(1);
        assertThat(game.path("players").get(0).path("name").asText()).isEqualTo("Alice");
        assertThat(game.path("state").isNull()).isTrue();
        assertThat(game.toString()).doesNotContain(body.path("playerToken").asText());
    }

    @Test
    void joinUpToFourPlayers() {
        String gameId = create("Host").path("gameId").asText();
        for (String name : new String[] {"B", "C", "D"}) {
            assertThat(post("/api/games/" + gameId + "/join", Map.of("name", name), null).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<JsonNode> fifth = post("/api/games/" + gameId + "/join", Map.of("name", "E"), null);

        assertError(fifth, HttpStatus.CONFLICT, "GAME_FULL");
        assertThat(get("/api/games/" + gameId).getBody().path("players")).hasSize(4);
    }

    @Test
    void startRules() {
        JsonNode host = create("Host");
        String gameId = host.path("gameId").asText();
        String hostToken = host.path("playerToken").asText();

        assertError(post("/api/games/" + gameId + "/start", null, null), HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        assertError(post("/api/games/" + gameId + "/start", null, "nope"), HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
        assertError(post("/api/games/" + gameId + "/start", null, hostToken), HttpStatus.CONFLICT,
                "NOT_ENOUGH_PLAYERS");

        String guestToken = post("/api/games/" + gameId + "/join", Map.of("name", "Guest"), null)
                .getBody().path("playerToken").asText();
        assertError(post("/api/games/" + gameId + "/start", null, guestToken), HttpStatus.FORBIDDEN, "NOT_HOST");

        ResponseEntity<JsonNode> started = post("/api/games/" + gameId + "/start", null, hostToken);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode state = started.getBody().path("state");
        assertThat(started.getBody().path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(state.path("version").asLong()).isZero();
        assertThat(state.path("phase").asText()).isEqualTo("ROLL");
        assertThat(state.path("activePlayerId").asText()).isEqualTo(host.path("playerId").asText());
        assertThat(state.path("players").get(1).path("coins").asInt()).isEqualTo(3);
        assertThat(state.path("players").get(1).path("establishments").path("WHEAT_FIELD").asInt()).isEqualTo(1);
        assertThat(state.path("supply").path("STADIUM").asInt()).isEqualTo(4);

        assertError(post("/api/games/" + gameId + "/start", null, hostToken), HttpStatus.CONFLICT,
                "GAME_ALREADY_STARTED");
        assertError(post("/api/games/" + gameId + "/join", Map.of("name", "Late"), null), HttpStatus.CONFLICT,
                "GAME_ALREADY_STARTED");
    }

    @Test
    void tokenOfAnotherGameCannotStart() {
        String gameId = create("A").path("gameId").asText();
        post("/api/games/" + gameId + "/join", Map.of("name", "B"), null);
        String otherToken = create("Other").path("playerToken").asText();

        assertError(post("/api/games/" + gameId + "/start", null, otherToken), HttpStatus.FORBIDDEN, "WRONG_GAME");
    }

    @Test
    void openLobbiesListsOnlyGamesNotStarted() {
        JsonNode open = create("Open host");
        JsonNode started = create("Started host");
        String startedId = started.path("gameId").asText();
        post("/api/games/" + startedId + "/join", Map.of("name", "B"), null);
        post("/api/games/" + startedId + "/start", null, started.path("playerToken").asText());

        JsonNode lobbies = get("/api/games").getBody();

        assertThat(lobbies.findValuesAsText("gameId"))
                .contains(open.path("gameId").asText())
                .doesNotContain(startedId);
        JsonNode entry = findLobby(lobbies, open.path("gameId").asText());
        assertThat(entry.path("hostName").asText()).isEqualTo("Open host");
        assertThat(entry.path("playerCount").asInt()).isEqualTo(1);
        assertThat(entry.path("maxPlayers").asInt()).isEqualTo(4);
    }

    @Test
    void unknownGame() {
        assertError(get("/api/games/NOPE00"), HttpStatus.NOT_FOUND, "GAME_NOT_FOUND");
        assertError(post("/api/games/NOPE00/join", Map.of("name", "X"), null), HttpStatus.NOT_FOUND, "GAME_NOT_FOUND");
    }

    @Test
    void nameIsValidated() {
        assertError(post("/api/games", Map.of("name", "  "), null), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertError(post("/api/games", Map.of("name", "x".repeat(25)), null), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertError(post("/api/games", Map.of(), null), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    }

    @Test
    void actuatorHealthIsUp() {
        assertThat(get("/actuator/health").getBody().path("status").asText()).isEqualTo("UP");
    }

    private JsonNode create(String name) {
        return post("/api/games", Map.of("name", name), null).getBody();
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add("X-Player-Token", token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path) {
        return rest.getForEntity(path, JsonNode.class);
    }

    private static JsonNode findLobby(JsonNode lobbies, String gameId) {
        for (JsonNode lobby : lobbies) {
            if (lobby.path("gameId").asText().equals(gameId)) {
                return lobby;
            }
        }
        throw new AssertionError("Lobby " + gameId + " not listed");
    }

    private static void assertError(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("message").asText()).isNotBlank();
    }
}
