package com.machikoro.server.api;

import com.machikoro.server.api.LobbyDtos.GameDetails;
import com.machikoro.server.api.LobbyDtos.JoinResponse;
import com.machikoro.server.api.LobbyDtos.LobbySummary;
import com.machikoro.server.api.LobbyDtos.NameRequest;
import com.machikoro.server.game.GameService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
class LobbyController {

    static final String TOKEN_HEADER = "X-Player-Token";

    private final GameService games;

    LobbyController(GameService games) {
        this.games = games;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    JoinResponse create(@Valid @RequestBody NameRequest request) {
        return games.create(request.name());
    }

    @PostMapping("/{gameId}/join")
    JoinResponse join(@PathVariable String gameId, @Valid @RequestBody NameRequest request) {
        return games.join(gameId, request.name());
    }

    @PostMapping("/{gameId}/start")
    GameDetails start(@PathVariable String gameId, @RequestHeader(TOKEN_HEADER) String token) {
        return games.start(gameId, token);
    }

    @GetMapping("/{gameId}")
    GameDetails get(@PathVariable String gameId) {
        return games.details(gameId);
    }

    @GetMapping
    List<LobbySummary> openLobbies() {
        return games.openLobbies();
    }
}
