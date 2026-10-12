package com.machikoro.core.event;

public record TurnPassed(String fromPlayerId, String toPlayerId) implements GameEvent {
}
