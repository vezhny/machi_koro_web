package com.machikoro.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/**
 * Two players over real WebSocket/STOMP: both see the same state after every move, and an invalid
 * command is answered with an error to the sender only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ScriptedDice.class)
class GameFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ScriptedDice dice;

    private StompPlayer alice;
    private StompPlayer bob;

    @BeforeEach
    void resetDice() {
        dice.clear();
    }

    @AfterEach
    void disconnect() {
        if (alice != null) {
            alice.close();
        }
        if (bob != null) {
            bob.close();
        }
    }

    @Test
    void twoPlayersShareStateAndErrorsGoOnlyToSender() throws Exception {
        JsonNode host = post("/api/games", Map.of("name", "Alice"), null);
        String gameId = host.path("gameId").asText();
        String aliceId = host.path("playerId").asText();
        JsonNode guest = post("/api/games/" + gameId + "/join", Map.of("name", "Bob"), null);
        String bobId = guest.path("playerId").asText();

        alice = StompPlayer.connect(port, gameId, host.path("playerToken").asText());
        bob = StompPlayer.connect(port, gameId, guest.path("playerToken").asText());

        // Before the start: commands are rejected, each player hears only about their own.
        // Receiving the error also proves that the subscriptions are active.
        alice.send(Map.of("type", "Pass", "expectedVersion", 0));
        bob.send(Map.of("type", "Pass", "expectedVersion", 0));
        assertThat(alice.nextError().path("code").asText()).isEqualTo("GAME_NOT_STARTED");
        assertThat(bob.nextError().path("code").asText()).isEqualTo("GAME_NOT_STARTED");

        post("/api/games/" + gameId + "/start", null, host.path("playerToken").asText());

        JsonNode aliceStart = alice.nextUpdate();
        assertThat(bob.nextUpdate()).isEqualTo(aliceStart);
        assertThat(aliceStart.path("state").path("version").asLong()).isZero();
        assertThat(aliceStart.path("events")).isEmpty();
        assertThat(alice.nextLobby().path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(bob.nextLobby().path("status").asText()).isEqualTo("IN_PROGRESS");

        // Bob tries to move out of turn: only Bob gets the error, the game does not change.
        bob.send(Map.of("type", "RollDice", "expectedVersion", 0, "diceCount", 1));
        JsonNode outOfTurn = bob.nextError();
        assertThat(outOfTurn.path("code").asText()).isEqualTo("NOT_YOUR_TURN");
        assertThat(outOfTurn.path("message").asText()).isNotBlank();

        // Alice rolls a 1: both Wheat Fields pay.
        dice.push(1);
        alice.send(Map.of("type", "RollDice", "expectedVersion", 0, "diceCount", 1));
        JsonNode rolled = alice.nextUpdate();
        assertThat(bob.nextUpdate()).isEqualTo(rolled);
        JsonNode state = rolled.path("state");
        assertThat(state.path("version").asLong()).isEqualTo(1);
        assertThat(state.path("phase").asText()).isEqualTo("BUILD");
        assertThat(state.path("turn").path("dice")).hasSize(1);
        assertThat(state.path("players").findValuesAsText("id")).containsExactly(aliceId, bobId);
        assertThat(state.path("players").get(0).path("coins").asInt()).isEqualTo(4);
        assertThat(state.path("players").get(1).path("coins").asInt()).isEqualTo(4);
        assertThat(types(rolled.path("events"))).containsExactly("DiceRolled", "IncomeReceived", "IncomeReceived");
        assertThat(rolled.path("events").get(1).path("card").asText()).isEqualTo("WHEAT_FIELD");

        // Alice buys a Ranch, which ends her turn.
        alice.send(Map.of("type", "Build", "expectedVersion", 1, "establishment", "RANCH"));
        JsonNode built = alice.nextUpdate();
        assertThat(bob.nextUpdate()).isEqualTo(built);
        assertThat(built.path("state").path("version").asLong()).isEqualTo(2);
        assertThat(built.path("state").path("activePlayerId").asText()).isEqualTo(bobId);
        assertThat(built.path("state").path("supply").path("RANCH").asInt()).isEqualTo(5);
        assertThat(types(built.path("events"))).containsExactly("Built", "TurnPassed");
        assertThat(built.path("events").get(0).path("target").path("kind").asText()).isEqualTo("ESTABLISHMENT");
        assertThat(built.path("events").get(0).path("target").path("establishment").asText()).isEqualTo("RANCH");

        // Bob uses an outdated version, then sends garbage.
        bob.send(Map.of("type", "RollDice", "expectedVersion", 1, "diceCount", 1));
        assertThat(bob.nextError().path("code").asText()).isEqualTo("STALE_VERSION");
        bob.send(Map.of("type", "Teleport", "expectedVersion", 2));
        assertThat(bob.nextError().path("code").asText()).isEqualTo("BAD_REQUEST");
        bob.send(Map.of("type", "Build", "expectedVersion", 2));
        assertThat(bob.nextError().path("code").asText()).isEqualTo("BAD_REQUEST");

        // Bob rolls and passes; Alice saw none of Bob's errors.
        dice.push(6);
        bob.send(Map.of("type", "RollDice", "expectedVersion", 2, "diceCount", 1));
        assertThat(bob.nextUpdate()).isEqualTo(alice.nextUpdate());
        bob.send(Map.of("type", "Pass", "expectedVersion", 3));
        JsonNode passed = bob.nextUpdate();
        assertThat(alice.nextUpdate()).isEqualTo(passed);
        assertThat(passed.path("state").path("activePlayerId").asText()).isEqualTo(aliceId);

        assertThat(alice.errors.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(alice.updates).isEmpty();
        assertThat(bob.updates).isEmpty();

        // Reconnect path: REST returns the same state as the last broadcast.
        JsonNode details = rest.getForObject("/api/games/" + gameId, JsonNode.class);
        assertThat(details.path("state")).isEqualTo(passed.path("state"));
    }

    @Test
    void connectWithoutValidTokenIsRefused() {
        assertThatThrownBy(() -> StompPlayer.connectRaw(port, "not-a-token")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> StompPlayer.connectRaw(port, null)).isInstanceOf(Exception.class);
    }

    @Test
    void cannotCommandAnotherGame() throws Exception {
        JsonNode first = post("/api/games", Map.of("name", "A"), null);
        JsonNode second = post("/api/games", Map.of("name", "B"), null);

        // Connected with a token of the first game, sending to the second game's destination.
        alice = StompPlayer.connect(port, first.path("gameId").asText(), first.path("playerToken").asText());
        alice.sendTo(second.path("gameId").asText(), Map.of("type", "Pass", "expectedVersion", 0));

        assertThat(alice.nextError().path("code").asText()).isEqualTo("WRONG_GAME");
    }

    private JsonNode post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add("X-Player-Token", token);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class).getBody();
    }

    private static List<String> types(JsonNode events) {
        return events.findValuesAsText("type");
    }
}
