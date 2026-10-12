package com.machikoro.core.engine;

import java.util.Objects;

/**
 * A command that breaks the rules. The game state is left unchanged.
 */
public final class IllegalMoveException extends RuntimeException {

    private final RuleViolation code;

    public IllegalMoveException(RuleViolation code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public RuleViolation code() {
        return code;
    }
}
