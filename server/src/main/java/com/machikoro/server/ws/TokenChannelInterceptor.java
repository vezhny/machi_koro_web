package com.machikoro.server.ws;

import com.machikoro.server.game.PlayerPrincipal;
import com.machikoro.server.game.PlayerTokens;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions by the {@code X-Player-Token} CONNECT header and restricts
 * subscriptions to the player's own game.
 */
@Component
class TokenChannelInterceptor implements ChannelInterceptor {

    static final String TOKEN_HEADER = "X-Player-Token";
    private static final String GAME_TOPIC_PREFIX = "/topic/games/";
    private static final String ERRORS_QUEUE = "/user/queue/errors";

    private final PlayerTokens tokens;

    TokenChannelInterceptor(PlayerTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> {
                PlayerPrincipal player = tokens.authenticate(accessor.getFirstNativeHeader(TOKEN_HEADER))
                        .orElseThrow(() -> new MessagingException("Invalid or missing " + TOKEN_HEADER));
                accessor.setUser(player);
            }
            case SUBSCRIBE -> checkSubscription(accessor);
            case SEND -> requirePlayer(accessor);
            default -> {
            }
        }
        return message;
    }

    private static void checkSubscription(StompHeaderAccessor accessor) {
        PlayerPrincipal player = requirePlayer(accessor);
        String destination = accessor.getDestination();
        String ownTopic = GAME_TOPIC_PREFIX + player.gameId();
        boolean allowed = ERRORS_QUEUE.equals(destination)
                || ownTopic.equals(destination)
                || (ownTopic + "/lobby").equals(destination);
        if (!allowed) {
            throw new MessagingException("Subscription to " + destination + " is not allowed");
        }
    }

    private static PlayerPrincipal requirePlayer(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof PlayerPrincipal player) {
            return player;
        }
        throw new MessagingException("Not authenticated");
    }
}
