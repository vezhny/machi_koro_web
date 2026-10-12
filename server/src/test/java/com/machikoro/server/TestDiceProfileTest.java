package com.machikoro.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.machikoro.server.api.CommandDto;
import com.machikoro.server.game.GameService;
import com.machikoro.server.game.PlayerTokens;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * The test-dice profile used by e2e tests: dice pushed over REST decide the next rolls.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-dice")
class TestDiceProfileTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private GameService games;

    @Autowired
    private PlayerTokens tokens;

    @Test
    void pushedDiceAreRolledNext() {
        rest.delete("/api/test/dice");
        assertThat(rest.postForEntity("/api/test/dice", Map.of("values", List.of(4, 5)), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForObject("/api/test/dice", JsonNode.class).path("remaining").asInt()).isEqualTo(2);

        JsonNode host = rest.postForObject("/api/games", Map.of("name", "A"), JsonNode.class);
        String gameId = host.path("gameId").asText();
        rest.postForObject("/api/games/" + gameId + "/join", Map.of("name", "B"), JsonNode.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Player-Token", host.path("playerToken").asText());
        rest.exchange("/api/games/" + gameId + "/start", HttpMethod.POST, new HttpEntity<>(headers), JsonNode.class);

        games.execute(gameId, tokens.authenticate(host.path("playerToken").asText()).orElseThrow(),
                new CommandDto.RollDiceDto(0, 1));

        JsonNode state = rest.getForObject("/api/games/" + gameId, JsonNode.class).path("state");
        assertThat(state.path("turn").path("dice").get(0).asInt()).isEqualTo(4);
        assertThat(rest.getForObject("/api/test/dice", JsonNode.class).path("remaining").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsImpossibleValues() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/test/dice",
                Map.of("values", List.of(7)), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("code").asText()).isEqualTo("BAD_REQUEST");
    }
}
