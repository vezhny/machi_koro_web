package com.machikoro.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.WebSocketRoute;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Turns played through the UI with scripted dice (server runs with the test-dice profile).
 */
class GameplayIT extends E2ETestBase {

    @Test
    void rollPaysIncomeToEveryone() {
        startTwoPlayerGame();
        server.pushDice(1);

        alice.rollOneDie();

        for (Player player : new Player[] {alice, bob}) {
            assertThat(player.coins("Alice")).hasText("🪙 4");
            assertThat(player.coins("Bob")).hasText("🪙 4");
            assertThat(player.status()).containsText("⚀");
            assertThat(player.log()).containsText("Alice бросил(а): ⚀ = 1");
            assertThat(player.log()).containsText("Bob +1 монета (Wheat Field)");
        }
        assertThat(alice.status()).containsText("Ваш ход — стройка");
        assertThat(bob.status()).containsText("Ходит Alice — стройка");
    }

    @Test
    void buyingACardEndsTheTurn() {
        startTwoPlayerGame();
        server.pushDice(6);
        alice.rollOneDie();
        assertThat(alice.status()).containsText("стройка");
        assertThat(alice.marketCard("Mine").getByRole(com.microsoft.playwright.options.AriaRole.BUTTON)).isDisabled();

        alice.buy("Ranch");

        assertThat(alice.coins("Alice")).hasText("🪙 2");
        assertThat(bob.coins("Alice")).hasText("🪙 2");
        assertThat(bob.playerPanel("Alice")).containsText("Ranch");
        assertThat(bob.marketCard("Ranch")).containsText("осталось 5");
        assertThat(bob.log()).containsText("Alice построил(а) Ranch");
        assertThat(alice.status()).containsText("Ходит Bob");
        assertThat(bob.status()).containsText("Ваш ход — бросок кубиков");
    }

    @Test
    void outOfTurnMoveIsRejectedForTheSenderOnly() {
        // Bob's browser stops hearing game broadcasts while muted, so his screen goes stale.
        AtomicBoolean muteBob = new AtomicBoolean();
        bob.page.routeWebSocket("**/ws", page -> {
            WebSocketRoute server = page.connectToServer();
            server.onMessage(frame -> {
                String text = frame.text();
                if (text == null) {
                    page.send(frame.binary());
                } else if (!(muteBob.get() && text.contains("destination:/topic/games/"))) {
                    page.send(text);
                }
            });
        });
        startTwoPlayerGame();
        server.pushDice(6, 6);
        alice.rollOneDie();
        alice.pass();
        bob.rollOneDie();
        assertThat(bob.button("Ничего не строить")).isVisible();

        muteBob.set(true);
        bob.pass();
        assertThat(alice.status()).containsText("Ваш ход — бросок кубиков");
        assertThat(bob.button("Ничего не строить")).isVisible();
        muteBob.set(false);

        bob.pass();

        assertThat(bob.toast()).hasText("Сейчас не ваш ход");
        assertThat(bob.status()).containsText("Ходит Alice — бросок кубиков");
        assertThat(alice.toast()).isEmpty();
        assertThat(alice.status()).containsText("Ваш ход — бросок кубиков");
    }

    @Test
    void reloadReconnectsToTheSameSeat() {
        startTwoPlayerGame();
        server.pushDice(1);
        alice.rollOneDie();
        assertThat(bob.coins("Bob")).hasText("🪙 4");

        bob.page.reload();

        assertThat(bob.page.locator(".topbar")).containsText("Bob");
        assertThat(bob.connection()).hasText("● онлайн");
        assertThat(bob.coins("Bob")).hasText("🪙 4");
        assertThat(bob.status()).containsText("Ходит Alice — стройка");

        // The reloaded tab still plays: it receives broadcasts and can move.
        alice.pass();
        assertThat(bob.status()).containsText("Ваш ход — бросок кубиков");
        server.pushDice(2);
        bob.rollOneDie();
        assertThat(alice.coins("Bob")).hasText("🪙 5");
        assertThat(bob.coins("Bob")).hasText("🪙 5");
    }
}
