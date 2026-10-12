package com.machikoro.server.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.machikoro.core.Establishment;
import com.machikoro.core.Landmark;
import com.machikoro.core.command.Build;
import com.machikoro.core.command.BuildTarget;
import com.machikoro.core.command.ChooseTarget;
import com.machikoro.core.command.Command;
import com.machikoro.core.command.KeepRoll;
import com.machikoro.core.command.Pass;
import com.machikoro.core.command.Reroll;
import com.machikoro.core.command.RollDice;

/**
 * Command as sent by a client to {@code /app/games/{id}/commands}. The player id is never taken
 * from the payload: it comes from the authenticated STOMP session.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CommandDto.RollDiceDto.class, name = "RollDice"),
        @JsonSubTypes.Type(value = CommandDto.RerollDto.class, name = "Reroll"),
        @JsonSubTypes.Type(value = CommandDto.KeepRollDto.class, name = "KeepRoll"),
        @JsonSubTypes.Type(value = CommandDto.ChooseTargetDto.class, name = "ChooseTarget"),
        @JsonSubTypes.Type(value = CommandDto.BuildDto.class, name = "Build"),
        @JsonSubTypes.Type(value = CommandDto.PassDto.class, name = "Pass"),
})
public sealed interface CommandDto {

    long expectedVersion();

    /**
     * @throws IllegalArgumentException if the payload is incomplete
     */
    Command toCommand(String playerId);

    record RollDiceDto(long expectedVersion, int diceCount) implements CommandDto {
        @Override
        public Command toCommand(String playerId) {
            return new RollDice(playerId, expectedVersion, diceCount);
        }
    }

    record RerollDto(long expectedVersion, int diceCount) implements CommandDto {
        @Override
        public Command toCommand(String playerId) {
            return new Reroll(playerId, expectedVersion, diceCount);
        }
    }

    record KeepRollDto(long expectedVersion) implements CommandDto {
        @Override
        public Command toCommand(String playerId) {
            return new KeepRoll(playerId, expectedVersion);
        }
    }

    record ChooseTargetDto(long expectedVersion, String targetPlayerId, Establishment give, Establishment take)
            implements CommandDto {
        @Override
        public Command toCommand(String playerId) {
            return new ChooseTarget(playerId, expectedVersion, targetPlayerId, give, take);
        }
    }

    /** Exactly one of {@code establishment} and {@code landmark} must be set. */
    record BuildDto(long expectedVersion, Establishment establishment, Landmark landmark) implements CommandDto {
        @Override
        public Command toCommand(String playerId) {
            if ((establishment == null) == (landmark == null)) {
                throw new IllegalArgumentException("Build needs exactly one of 'establishment' or 'landmark'");
            }
            BuildTarget target = establishment != null ? BuildTarget.of(establishment) : BuildTarget.of(landmark);
            return new Build(playerId, expectedVersion, target);
        }
    }

    record PassDto(long expectedVersion) implements CommandDto {
        @Override
        public Command toCommand(String playerId) {
            return new Pass(playerId, expectedVersion);
        }
    }
}
