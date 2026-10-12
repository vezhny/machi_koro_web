package com.machikoro.server.api;

import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.Phase;
import com.machikoro.core.PlayerState;
import com.machikoro.core.TurnContext;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Game state as sent to clients. Decoupled from {@link GameState} so the wire format does not
 * change with internal helpers of the core model.
 */
public record GameView(
        String gameId,
        long version,
        Phase phase,
        String activePlayerId,
        List<PlayerView> players,
        Map<Establishment, Integer> supply,
        TurnView turn,
        String winnerId) {

    public record PlayerView(
            String id,
            String name,
            int coins,
            Map<Establishment, Integer> establishments,
            Set<Landmark> landmarks) {

        static PlayerView from(PlayerState player) {
            return new PlayerView(player.id(), player.name(), player.coins(), player.establishments(),
                    player.landmarks());
        }
    }

    /**
     * @param dice           empty until the active player rolls
     * @param pendingTargets cards waiting for a {@code ChooseTarget}; the first one is current
     */
    public record TurnView(List<Integer> dice, boolean rerollUsed, List<Establishment> pendingTargets) {

        static TurnView from(TurnContext turn) {
            return new TurnView(turn.dice(), turn.rerollUsed(), turn.pendingTargets());
        }
    }

    public static GameView from(GameState state) {
        return new GameView(
                state.gameId(),
                state.version(),
                state.phase(),
                state.activePlayer().id(),
                state.players().stream().map(PlayerView::from).toList(),
                state.supply(),
                TurnView.from(state.turn()),
                state.winnerId());
    }
}
