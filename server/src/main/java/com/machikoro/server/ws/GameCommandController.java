package com.machikoro.server.ws;

import com.machikoro.core.engine.IllegalMoveException;
import com.machikoro.server.api.CommandDto;
import com.machikoro.server.api.LobbyDtos.ErrorMessage;
import com.machikoro.server.game.GameService;
import com.machikoro.server.game.LobbyError;
import com.machikoro.server.game.LobbyException;
import com.machikoro.server.game.PlayerPrincipal;
import java.security.Principal;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * Receives game commands. Successful results are broadcast by {@link GameService}; errors go back
 * only to the session that sent the command.
 */
@Controller
class GameCommandController {

    private static final String ERRORS = "/queue/errors";

    private final GameService games;

    GameCommandController(GameService games) {
        this.games = games;
    }

    @MessageMapping("/games/{gameId}/commands")
    void command(@DestinationVariable String gameId, @Payload CommandDto command, Principal principal) {
        games.execute(gameId, (PlayerPrincipal) principal, command);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS, broadcast = false)
    ErrorMessage illegalMove(IllegalMoveException e) {
        return new ErrorMessage(e.code().name(), e.getMessage());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERRORS, broadcast = false)
    ErrorMessage lobby(LobbyException e) {
        return new ErrorMessage(e.code().name(), e.getMessage());
    }

    @MessageExceptionHandler({MessageConversionException.class, IllegalArgumentException.class})
    @SendToUser(destinations = ERRORS, broadcast = false)
    ErrorMessage badRequest(Exception e) {
        return new ErrorMessage(LobbyError.BAD_REQUEST.name(), "Malformed command: " + e.getMessage());
    }
}
