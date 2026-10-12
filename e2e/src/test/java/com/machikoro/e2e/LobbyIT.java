package com.machikoro.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;

/**
 * Creating and joining a game from two browser contexts.
 */
class LobbyIT extends E2ETestBase {

    @Test
    void hostCreatesGuestJoinsByCodeHostStarts() {
        String code = alice.createGame();
        assertEquals(6, code.length(), "game code " + code);
        assertThat(alice.button("Нужен хотя бы ещё один игрок")).isDisabled();

        bob.joinByCode(code);

        // The host's waiting room updates live.
        assertThat(alice.seats().locator("li")).hasCount(2);
        assertThat(alice.seats()).containsText("Bob");
        assertThat(bob.seats()).containsText("Alice");
        assertThat(bob.page.getByText("Ждём, пока хост начнёт игру")).isVisible();
        assertThat(bob.button("Начать игру")).hasCount(0);

        alice.button("Начать игру").click();

        assertThat(alice.status()).containsText("Ваш ход — бросок кубиков");
        assertThat(bob.status()).containsText("Ходит Alice");
        for (Player player : new Player[] {alice, bob}) {
            assertThat(player.coins("Alice")).hasText("🪙 3");
            assertThat(player.coins("Bob")).hasText("🪙 3");
            assertThat(player.playerPanel("Bob")).containsText("Wheat Field");
            assertThat(player.playerPanel("Bob")).containsText("Bakery");
        }
        assertThat(bob.button("Бросить 1 кубик")).hasCount(0);
    }

    @Test
    void guestJoinsFromOpenGamesList() {
        String code = alice.createGame();

        bob.openHome();
        Locator lobby = bob.page.locator(".lobbies li").filter(new Locator.FilterOptions().setHasText(code));
        assertThat(lobby).containsText("Alice");
        lobby.getByRole(AriaRole.BUTTON).click();

        assertThat(bob.seats()).containsText("Bob");
        assertThat(alice.seats()).containsText("Bob");
    }
}
