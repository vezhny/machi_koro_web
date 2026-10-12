package com.machikoro.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared server and browser; every test gets two fresh, isolated browser contexts: Alice and Bob.
 * Run with {@code -De2e.headed=true} to watch.
 */
abstract class E2ETestBase {

    protected static ServerProcess server;
    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext aliceContext;
    private BrowserContext bobContext;
    protected Player alice;
    protected Player bob;

    @BeforeAll
    static void launch() {
        server = ServerProcess.shared();
        PlaywrightAssertions.setDefaultAssertionTimeout(10_000);
        playwright = Playwright.create(new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(!Boolean.getBoolean("e2e.headed")));
    }

    @AfterAll
    static void shutDown() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openPlayers() {
        server.clearDice();
        Browser.NewContextOptions options = new Browser.NewContextOptions().setBaseURL(server.baseUrl());
        aliceContext = browser.newContext(options);
        bobContext = browser.newContext(options);
        alice = new Player("Alice", aliceContext.newPage());
        bob = new Player("Bob", bobContext.newPage());
    }

    @AfterEach
    void closePlayers() {
        aliceContext.close();
        bobContext.close();
    }

    /** Alice hosts, Bob joins by code, Alice starts. Alice moves first. */
    protected String startTwoPlayerGame() {
        String code = alice.createGame();
        bob.joinByCode(code);
        assertThat(alice.seats()).containsText("Bob");
        alice.button("Начать игру").click();
        assertThat(alice.status()).containsText("Ваш ход — бросок кубиков");
        assertThat(bob.status()).containsText("Ходит Alice");
        return code;
    }
}
