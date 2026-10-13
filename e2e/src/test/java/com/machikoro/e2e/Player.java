package com.machikoro.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * One player's browser page, with the few interactions and locators the scenarios need.
 */
final class Player {

    final String name;
    final Page page;

    Player(String name, Page page) {
        this.name = name;
        this.page = page;
    }

    void openHome() {
        page.navigate("/");
        page.locator("#name").fill(name);
    }

    /** Creates a game and returns its code from the waiting room. */
    String createGame() {
        openHome();
        button("Создать партию").click();
        Locator code = page.locator("code.big");
        assertThat(code).isVisible();
        return code.textContent().trim();
    }

    void joinByCode(String code) {
        openHome();
        page.locator("input[name=code]").fill(code);
        button("Войти по коду").click();
        assertThat(seats()).containsText(name);
    }

    Locator button(String text) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text).setExact(true));
    }

    void rollOneDie() {
        button("Бросить 1 кубик").click();
    }

    void pass() {
        button("Ничего не строить").click();
    }

    void buy(String cardName) {
        marketCard(cardName).getByRole(AriaRole.BUTTON).click();
    }

    Locator seats() {
        return page.locator(".seats");
    }

    Locator status() {
        return page.locator(".status");
    }

    Locator log() {
        return page.locator(".log");
    }

    Locator toast() {
        return page.locator("#toast");
    }

    Locator connection() {
        return page.locator(".conn");
    }

    Locator playerPanel(String playerName) {
        return page.locator(".player").filter(new Locator.FilterOptions()
                .setHas(page.locator(".name").getByText(playerName, new Locator.GetByTextOptions().setExact(false))));
    }

    Locator coins(String playerName) {
        return playerPanel(playerName).locator(".coins");
    }

    Locator marketCard(String cardName) {
        return page.locator(".market .card").filter(new Locator.FilterOptions()
                .setHas(page.locator(".card-name").getByText(cardName, new Locator.GetByTextOptions().setExact(true))));
    }
}
