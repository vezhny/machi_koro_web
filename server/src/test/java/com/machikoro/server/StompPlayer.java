package com.machikoro.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Test STOMP client of one player: collects game updates, lobby updates and own errors.
 */
final class StompPlayer implements AutoCloseable {

    private static final long TIMEOUT_SECONDS = 5;

    final BlockingQueue<JsonNode> updates = new LinkedBlockingQueue<>();
    final BlockingQueue<JsonNode> lobby = new LinkedBlockingQueue<>();
    final BlockingQueue<JsonNode> errors = new LinkedBlockingQueue<>();

    private final WebSocketStompClient client;
    private final StompSession session;
    private final String gameId;

    private StompPlayer(WebSocketStompClient client, StompSession session, String gameId) {
        this.client = client;
        this.session = session;
        this.gameId = gameId;
    }

    static StompSession connectRaw(int port, String token) throws Exception {
        return newClient().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                tokenHeaders(token), new StompSessionHandlerAdapter() {
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    static StompPlayer connect(int port, String gameId, String token) throws Exception {
        WebSocketStompClient client = newClient();
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                tokenHeaders(token), new StompSessionHandlerAdapter() {
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        StompPlayer player = new StompPlayer(client, session, gameId);
        session.subscribe("/topic/games/" + gameId, collectInto(player.updates));
        session.subscribe("/topic/games/" + gameId + "/lobby", collectInto(player.lobby));
        session.subscribe("/user/queue/errors", collectInto(player.errors));
        return player;
    }

    void send(Object command) {
        sendTo(gameId, command);
    }

    void sendTo(String targetGameId, Object command) {
        session.send("/app/games/" + targetGameId + "/commands", command);
    }

    JsonNode nextUpdate() throws InterruptedException {
        return take(updates, "game update");
    }

    JsonNode nextLobby() throws InterruptedException {
        return take(lobby, "lobby update");
    }

    JsonNode nextError() throws InterruptedException {
        return take(errors, "error");
    }

    private static JsonNode take(BlockingQueue<JsonNode> queue, String what) throws InterruptedException {
        JsonNode message = queue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (message == null) {
            throw new AssertionError("No " + what + " within " + TIMEOUT_SECONDS + "s");
        }
        return message;
    }

    @Override
    public void close() {
        session.disconnect();
        client.stop();
    }

    private static WebSocketStompClient newClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private static StompHeaders tokenHeaders(String token) {
        StompHeaders headers = new StompHeaders();
        if (token != null) {
            headers.add("X-Player-Token", token);
        }
        return headers;
    }

    private static StompFrameHandler collectInto(BlockingQueue<JsonNode> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        };
    }
}
