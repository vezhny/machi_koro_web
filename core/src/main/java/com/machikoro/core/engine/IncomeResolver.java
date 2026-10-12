package com.machikoro.core.engine;

import com.machikoro.core.CardColor;
import com.machikoro.core.CardIcon;
import com.machikoro.core.Establishment;
import com.machikoro.core.GameState;
import com.machikoro.core.Landmark;
import com.machikoro.core.PlayerState;
import com.machikoro.core.event.CoinsTransferred;
import com.machikoro.core.event.GameEvent;
import com.machikoro.core.event.IncomeReceived;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Activates cards for the current dice sum: red, then blue, then green, then purple.
 */
public final class IncomeResolver {

    private static final int STADIUM_AMOUNT = 2;
    private static final int TV_STATION_AMOUNT = 5;

    private static final Map<CardColor, List<Establishment>> BY_COLOR = Arrays.stream(Establishment.values())
            .collect(Collectors.groupingBy(Establishment::color));

    /**
     * @param pendingTargets activated TV Station / Business Center, still waiting for a target choice
     */
    public record Resolution(GameState state, List<GameEvent> events, List<Establishment> pendingTargets) {

        public Resolution {
            events = List.copyOf(events);
            pendingTargets = List.copyOf(pendingTargets);
        }
    }

    public Resolution resolve(GameState state) {
        int sum = state.turn().diceSum();
        List<GameEvent> events = new ArrayList<>();
        List<Establishment> pending = new ArrayList<>();

        GameState current = resolveRed(state, sum, events);
        current = resolveBlue(current, sum, events);
        current = resolveGreen(current, sum, events);
        current = resolvePurple(current, sum, events, pending);
        return new Resolution(current, events, pending);
    }

    /** Coins taken by TV Station from the target: up to 5. */
    public static int tvStationAmount(PlayerState target) {
        return Math.min(TV_STATION_AMOUNT, target.coins());
    }

    /**
     * Opponents' red cards, counterclockwise from the active player (index active-1, active-2, ...).
     * The active player pays what they can; later owners may get less or nothing.
     */
    private GameState resolveRed(GameState state, int sum, List<GameEvent> events) {
        int playerCount = state.players().size();
        int active = state.activePlayerIndex();
        GameState current = state;
        for (int step = 1; step < playerCount; step++) {
            String ownerId = current.players().get(Math.floorMod(active - step, playerCount)).id();
            for (Establishment card : cards(CardColor.RED, sum)) {
                PlayerState owner = current.player(ownerId);
                PlayerState payer = current.activePlayer();
                int paid = Math.min(owner.count(card) * incomePerCopy(owner, card), payer.coins());
                if (paid > 0) {
                    current = transfer(current, payer.id(), ownerId, paid);
                    events.add(new CoinsTransferred(payer.id(), ownerId, card, paid));
                }
            }
        }
        return current;
    }

    /** Everyone's blue cards, income from the bank. */
    private GameState resolveBlue(GameState state, int sum, List<GameEvent> events) {
        GameState current = state;
        for (PlayerState player : state.players()) {
            current = collectFromBank(current, player.id(), CardColor.BLUE, sum, events);
        }
        return current;
    }

    /** Active player's green cards, income from the bank. */
    private GameState resolveGreen(GameState state, int sum, List<GameEvent> events) {
        return collectFromBank(state, state.activePlayer().id(), CardColor.GREEN, sum, events);
    }

    /** Active player's purple cards. Stadium resolves at once, targeted cards are queued. */
    private GameState resolvePurple(GameState state, int sum, List<GameEvent> events, List<Establishment> pending) {
        GameState current = state;
        PlayerState active = state.activePlayer();
        for (Establishment card : cards(CardColor.PURPLE, sum)) {
            if (active.count(card) == 0) {
                continue;
            }
            if (card == Establishment.STADIUM) {
                current = resolveStadium(current, events);
            } else {
                pending.add(card);
            }
        }
        return current;
    }

    private GameState resolveStadium(GameState state, List<GameEvent> events) {
        int playerCount = state.players().size();
        int active = state.activePlayerIndex();
        String activeId = state.activePlayer().id();
        GameState current = state;
        for (int step = 1; step < playerCount; step++) {
            PlayerState opponent = current.players().get((active + step) % playerCount);
            int paid = Math.min(STADIUM_AMOUNT, opponent.coins());
            if (paid > 0) {
                current = transfer(current, opponent.id(), activeId, paid);
                events.add(new CoinsTransferred(opponent.id(), activeId, Establishment.STADIUM, paid));
            }
        }
        return current;
    }

    private GameState collectFromBank(GameState state, String playerId, CardColor color, int sum,
                                      List<GameEvent> events) {
        GameState current = state;
        for (Establishment card : cards(color, sum)) {
            PlayerState player = current.player(playerId);
            int amount = player.count(card) * incomePerCopy(player, card);
            if (amount > 0) {
                current = current.withPlayer(player.withCoins(player.coins() + amount));
                events.add(new IncomeReceived(playerId, card, amount));
            }
        }
        return current;
    }

    /** Coins one copy of a blue, green or red card yields for its owner, Shopping Mall included. */
    static int incomePerCopy(PlayerState owner, Establishment card) {
        int base = switch (card) {
            case WHEAT_FIELD, RANCH, BAKERY, CAFE, FOREST -> 1;
            case FAMILY_RESTAURANT -> 2;
            case CONVENIENCE_STORE, APPLE_ORCHARD -> 3;
            case MINE -> 5;
            case CHEESE_FACTORY -> 3 * owner.countIcon(CardIcon.COW);
            case FURNITURE_FACTORY -> 3 * owner.countIcon(CardIcon.GEAR);
            case FRUIT_AND_VEGETABLE_MARKET -> 2 * owner.countIcon(CardIcon.WHEAT);
            case STADIUM, TV_STATION, BUSINESS_CENTER ->
                    throw new IllegalArgumentException("Purple cards have no per-copy income: " + card);
        };
        boolean boosted = card.icon() == CardIcon.BREAD || card.icon() == CardIcon.CUP;
        return boosted && owner.has(Landmark.SHOPPING_MALL) ? base + 1 : base;
    }

    private static List<Establishment> cards(CardColor color, int sum) {
        return BY_COLOR.getOrDefault(color, List.of()).stream()
                .filter(card -> card.activatesOn(sum))
                .toList();
    }

    static GameState transfer(GameState state, String fromId, String toId, int amount) {
        PlayerState from = state.player(fromId);
        GameState afterPay = state.withPlayer(from.withCoins(from.coins() - amount));
        PlayerState to = afterPay.player(toId);
        return afterPay.withPlayer(to.withCoins(to.coins() + amount));
    }
}
