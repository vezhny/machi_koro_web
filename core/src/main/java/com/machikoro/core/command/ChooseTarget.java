package com.machikoro.core.command;

import com.machikoro.core.Establishment;

/**
 * Resolves the pending TV Station or Business Center.
 *
 * <ul>
 *   <li>TV Station: {@code targetPlayerId} is required, {@code give}/{@code take} are ignored.</li>
 *   <li>Business Center: {@code give} (own card) is swapped for {@code take} (target's card).
 *       A {@code null} {@code targetPlayerId} skips the swap.</li>
 * </ul>
 */
public record ChooseTarget(
        String playerId,
        long expectedVersion,
        String targetPlayerId,
        Establishment give,
        Establishment take) implements Command {
}
