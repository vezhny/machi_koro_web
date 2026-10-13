package com.machikoro.server.testsupport;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets end-to-end tests decide the dice. Exists only with the {@code test-dice} profile.
 */
@RestController
@Profile(TestDice.PROFILE)
@RequestMapping("/api/test/dice")
class TestDiceController {

    record DiceRequest(@NotNull List<Integer> values) {
    }

    record DiceQueue(int remaining) {
    }

    private final TestDice dice;

    TestDiceController(TestDice dice) {
        this.dice = dice;
    }

    /** Appends values to the queue: {@code {"values": [3, 4]}}. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void push(@Validated @RequestBody DiceRequest request) {
        dice.push(request.values());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clear() {
        dice.clear();
    }

    @GetMapping
    DiceQueue remaining() {
        return new DiceQueue(dice.remaining());
    }
}
