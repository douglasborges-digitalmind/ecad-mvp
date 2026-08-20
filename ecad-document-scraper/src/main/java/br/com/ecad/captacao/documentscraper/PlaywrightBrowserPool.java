package br.com.ecad.captacao.documentscraper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * Pool singleton de Browser do Playwright.
 *
 * <p>Evita o custo de ~2s de startup do Chromium em cada chamada de scraping,
 * reutilizando uma unica instancia de Browser durante todo o ciclo de vida da aplicacao.
 * O Spring gerencia o fechamento automatico via {@link PreDestroy}.
 */
@Component
class PlaywrightBrowserPool {
    private final Playwright playwright;
    private final Browser browser;

    PlaywrightBrowserPool() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    Browser getBrowser() {
        return browser;
    }

    @PreDestroy
    void close() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }
}
