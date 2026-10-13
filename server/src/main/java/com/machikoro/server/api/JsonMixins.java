package com.machikoro.server.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.machikoro.core.command.BuildTarget;
import com.machikoro.core.event.GameEvent;
import org.springframework.boot.jackson.JsonMixin;

/**
 * Adds JSON type discriminators to core types without putting Jackson annotations into core.
 */
final class JsonMixins {

    private JsonMixins() {
    }

    /** Events carry {@code "type": "<simple class name>"}, e.g. {@code "DiceRolled"}. */
    @JsonMixin(GameEvent.class)
    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
    interface GameEventMixin {
    }

    /** {@code {"kind": "ESTABLISHMENT", "establishment": "RANCH"}} or {@code {"kind": "LANDMARK", ...}}. */
    @JsonMixin(BuildTarget.class)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = BuildTarget.EstablishmentTarget.class, name = "ESTABLISHMENT"),
            @JsonSubTypes.Type(value = BuildTarget.LandmarkTarget.class, name = "LANDMARK"),
    })
    interface BuildTargetMixin {
    }
}
