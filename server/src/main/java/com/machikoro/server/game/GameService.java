package com.machikoro.server.game;

import com.machikoro.core.GameSetup;
import com.machikoro.core.PlayerInfo;
import com.machikoro.core.command.Command;
import com.machikoro.core.engine.GameEngine;
import com.machikoro.core.engine.GameResult;
import com.machikoro.server.api.CommandDto;
import com.machikoro.server.api.GameUpdate;
import com.machikoro.server.api.GameView;
import com.machikoro.server.api.LobbyDtos.GameDetails;
import com.machikoro.server.api.LobbyDtos.JoinResponse;
import com.machikoro.server.api.LobbyDtos.LobbySummary;
import com.machikoro.server.api.LobbyDtos.LobbyView;
import com.machikoro.server.api.LobbyDtos.PlayerSummary;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

/**
 * In-memory games: lobby, player tokens and command processing. Every game is mutated only under
 * its own lock, so commands of one game are applied and broadcast strictly in order.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private static final int GAME_ID_LENGTH = 6;
    private static final String GAME_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Map<String, GameSession> games = new ConcurrentHashMap<>();
    private final GameEngine engine = new GameEngine();
    private final SecureRandom random = new SecureRandom();

    private final PlayerTokens tokens;
    private final DiceFactory diceFactory;
    private final SimpMessageSendingOperations messaging;
    private final Clock clock;

    public GameService(PlayerTokens tokens, DiceFactory diceFactory, SimpMessageSendingOperations messaging,
                       Clock clock) {
        this.tokens = tokens;
        this.diceFactory = diceFactory;
        this.messaging = messaging;
        this.clock = clock;
    }

    public static String gameTopic(String gameId) {
        return "/topic/games/" + gameId;
    }

    public static String lobbyTopic(String gameId) {
        return gameTopic(gameId) + "/lobby";
    }

    /** Creates a game and seats its creator as host. */
    public JoinResponse create(String hostName) {
        String hostId = newPlayerId();
        long seed = random.nextLong();
        GameSession session;
        do {
            session = new GameSession(newGameId(), hostId, seed, diceFactory.create(seed));
        } while (games.putIfAbsent(session.id, session) != null);
        session.seats.add(new PlayerInfo(hostId, hostName.strip()));
        log.info("Game {} created by {}", session.id, hostId);
        return issueToken(session.id, hostId);
    }

    public JoinResponse join(String gameId, String name) {
        GameSession session = session(gameId);
        return locked(session, () -> {
            if (session.status() != GameStatus.LOBBY) {
                throw new LobbyException(LobbyError.GAME_ALREADY_STARTED, "Game " + gameId + " has already started");
            }
            if (session.seats.size() >= GameSetup.MAX_PLAYERS) {
                throw new LobbyException(LobbyError.GAME_FULL, "Game " + gameId + " is full");
            }
            String playerId = newPlayerId();
            session.seats.add(new PlayerInfo(playerId, name.strip()));
            messaging.convertAndSend(lobbyTopic(gameId), lobbyView(session));
            return issueToken(gameId, playerId);
        });
    }

    /** Only the host can start, with at least two players seated. */
    public GameDetails start(String gameId, String token) {
        PlayerPrincipal player = tokens.authenticate(token)
                .orElseThrow(() -> new LobbyException(LobbyError.INVALID_TOKEN, "Unknown player token"));
        GameSession session = session(gameId);
        return locked(session, () -> {
            if (!player.gameId().equals(gameId)) {
                throw new LobbyException(LobbyError.WRONG_GAME, "Token belongs to another game");
            }
            if (!session.hostPlayerId.equals(player.playerId())) {
                throw new LobbyException(LobbyError.NOT_HOST, "Only the host can start the game");
            }
            if (session.status() != GameStatus.LOBBY) {
                throw new LobbyException(LobbyError.GAME_ALREADY_STARTED, "Game " + gameId + " has already started");
            }
            if (session.seats.size() < GameSetup.MIN_PLAYERS) {
                throw new LobbyException(LobbyError.NOT_ENOUGH_PLAYERS,
                        "At least " + GameSetup.MIN_PLAYERS + " players are needed");
            }
            session.state = GameSetup.newGame(gameId, List.copyOf(session.seats));
            log.info("Game {} started with {} players", gameId, session.seats.size());
            messaging.convertAndSend(lobbyTopic(gameId), lobbyView(session));
            messaging.convertAndSend(gameTopic(gameId), new GameUpdate(GameView.from(session.state), List.of()));
            return details(session);
        });
    }

    public GameDetails details(String gameId) {
        GameSession session = session(gameId);
        return locked(session, () -> details(session));
    }

    public List<LobbySummary> openLobbies() {
        return games.values().stream()
                .map(session -> locked(session, () -> session.status() == GameStatus.LOBBY
                        ? new LobbySummary(session.id, session.seats.getFirst().name(), session.seats.size(),
                        GameSetup.MAX_PLAYERS)
                        : null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(LobbySummary::gameId))
                .toList();
    }

    /**
     * Validates and applies a command, then broadcasts the new state to everyone in the game.
     *
     * @throws com.machikoro.core.engine.IllegalMoveException if the command breaks the rules
     * @throws LobbyException                                 if the game is not running or not the player's
     * @throws IllegalArgumentException                       if the payload is incomplete
     */
    public void execute(String gameId, PlayerPrincipal player, CommandDto dto) {
        if (!player.gameId().equals(gameId)) {
            throw new LobbyException(LobbyError.WRONG_GAME, "You are not a player of game " + gameId);
        }
        GameSession session = session(gameId);
        Command command = dto.toCommand(player.playerId());
        locked(session, () -> {
            if (session.state == null) {
                throw new LobbyException(LobbyError.GAME_NOT_STARTED, "Game " + gameId + " has not started");
            }
            GameResult result = engine.apply(session.state, command, session.dice);
            session.state = result.state();
            session.log.add(new GameSession.LoggedCommand(clock.instant(), command));
            messaging.convertAndSend(gameTopic(gameId), GameUpdate.from(result));
            return null;
        });
    }

    private GameSession session(String gameId) {
        GameSession session = gameId == null ? null : games.get(gameId);
        if (session == null) {
            throw new LobbyException(LobbyError.GAME_NOT_FOUND, "Game " + gameId + " not found");
        }
        return session;
    }

    private static <T> T locked(GameSession session, Supplier<T> action) {
        session.lock.lock();
        try {
            return action.get();
        } finally {
            session.lock.unlock();
        }
    }

    private JoinResponse issueToken(String gameId, String playerId) {
        return new JoinResponse(gameId, playerId, tokens.issue(gameId, playerId));
    }

    private static GameDetails details(GameSession session) {
        LobbyView lobby = lobbyView(session);
        GameView state = session.state == null ? null : GameView.from(session.state);
        return new GameDetails(lobby.gameId(), lobby.status(), lobby.hostPlayerId(), lobby.players(), state);
    }

    private static LobbyView lobbyView(GameSession session) {
        List<PlayerSummary> players = session.seats.stream()
                .map(seat -> new PlayerSummary(seat.id(), seat.name()))
                .toList();
        return new LobbyView(session.id, session.status(), session.hostPlayerId, players);
    }

    /** Short, unambiguous code that players can dictate to each other. */
    private String newGameId() {
        StringBuilder id = new StringBuilder(GAME_ID_LENGTH);
        for (int i = 0; i < GAME_ID_LENGTH; i++) {
            id.append(GAME_ID_ALPHABET.charAt(random.nextInt(GAME_ID_ALPHABET.length())));
        }
        return id.toString();
    }

    private static String newPlayerId() {
        return UUID.randomUUID().toString();
    }
}
