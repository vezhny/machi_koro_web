package com.machikoro.core.command;

import java.util.Objects;

/**
 * Buy one establishment or landmark. Ends the turn.
 */
public record Build(String playerId, long expectedVersion, BuildTarget target) implements Command {

    public Build {
        Objects.requireNonNull(target, "target");
    }
}
