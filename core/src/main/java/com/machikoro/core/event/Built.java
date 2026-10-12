package com.machikoro.core.event;

import com.machikoro.core.command.BuildTarget;

public record Built(String playerId, BuildTarget target) implements GameEvent {
}
