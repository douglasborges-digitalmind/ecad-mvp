package br.com.ecad.captacao.documentscraper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class DefaultHybridBrowserService implements HybridBrowserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHybridBrowserService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final double PNCP_WAIT_TIMEOUT_MS = 30_000;
    private static final String PNCP_HOST_SUFFIX = "pncp.gov.br";
    // Seletor especifico dos itens de resultado de busca do PNCP (espelha o scraper .NET de referencia).
    // Evita capturar os links fixos do menu (/app/editais, /app/atas, /app/contratos...), que antes
    // poluiam a descoberta e faziam o pipeline "encontrar" apenas ~4 itens de navegacao por execucao.
    private static final String PNCP_ITEM_SELECTOR = "a.br-item[title=\"Acessar item.\"]";
    // Botao de download do detalhe do contrato. O endpoint real de download nao termina em ".pdf",
    // portanto filtrar por href*=".pdf" descartava todos os documentos.
    private static final String PNCP_DOWNLOAD_SELECTOR = "a[aria-label=\"Fazer download\"]";
    // Conteudo principal do contrato. Esperar este elemento (espelha o WaitForLoadState/auto-wait do .NET)
    // antes de procurar o botao de download evita timeouts em massa na SPA Angular do PNCP.
    private static final String PNCP_CONTRATO_WAIT_SELECTOR = "h1";
    // Aba "Arquivos" na pagina de detalhe do PNCP. O botao de download (a[aria-label="Fazer download"])
    // fica dentro do conteudo desta aba, que so e renderizado quando a aba recebe um clique.
    // Sem este clique, o DOM do download nunca e criado e o scraper retorna null para todos os contratos.
    private static final String PNCP_ARQUIVOS_TAB_SELECTOR = "a.nav-link, button.nav-link, a[role='tab'], button[role='tab']";
    private static final Pattern ANCHOR_PATTERN = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern PDF_PATTERN = Pattern.compile("https?://\\S+?\\.pdf(?:\\?\\S*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final PlaywrightBrowserPool browserPool;

    DefaultHybridBrowserService(HttpClient httpClient, PlaywrightBrowserPool browserPool) {
        this.httpClient = httpClient;
        this.browserPool = browserPool;
    }

    @Override
    public String getMarkdownHttp(String url) throws Exception {
        return getMarkdownHttp(url, null);
    }

    @Override
    public String getMarkdownHttp(String url, String postBody) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).header("User-Agent", "ECAD-DocumentScraper/1.0");
        var request = postBody == null
            ? builder.GET().build()
            : builder.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(postBody)).build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " ao carregar " + url);
        }
        return htmlToMarkdown(response.body(), url);
    }

    @Override
    public String getMarkdown(String url, String waitForSelector, String fillSelector, String fillText, String expandSelector,
        String iframeSelector, String submitSelector, String evaluateJs) throws Exception {
        var page = browserPool.getBrowser().newPage();
        try {
            page.navigate(url);
            if (waitForSelector != null && !waitForSelector.isBlank()) {
                page.waitForSelector(waitForSelector);
            }
            if (expandSelector != null && !expandSelector.isBlank()) {
                page.locator(expandSelector).click();
            }
            if (fillSelector != null && !fillSelector.isBlank()) {
                page.locator(fillSelector).fill(fillText == null ? "" : fillText);
            }
            if (submitSelector != null && !submitSelector.isBlank()) {
                page.locator(submitSelector).click();
            }
            if (evaluateJs != null && !evaluateJs.isBlank()) {
                page.evaluate(evaluateJs);
            }
            return htmlToMarkdown(page.content(), url);
        } finally {
            page.close();
        }
    }

    @Override
    public byte[] download(String url) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).header("User-Agent", "ECAD-DocumentScraper/1.0").GET().build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " ao baixar " + url);
        }
        return response.body();
    }

    @Override
    public byte[] captureScreenshot(String url) {
        var page = browserPool.getBrowser().newPage();
        try {
            page.navigate(url);
            return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
        } finally {
            page.close();
        }
    }

    @Override
    public List<String> discoverPncpDetailLinks(String url) throws Exception {
        if (isPncpHost(url)) {
            return discoverPncpDetailLinksViaPlaywright(url);
        }
        // Fallback HTTP para sites estaticos / testes locais (nao-PNCP): mantem extracao por regex.
        var markdown = getMarkdownHttp(url);
        var links = new LinkedHashSet<String>();
        var matcher = URL_PATTERN.matcher(markdown);
        while (matcher.find()) {
            links.add(cleanUrl(matcher.group()));
        }
        return new ArrayList<>(links);
    }

    @Override
    public PncpDetailResult fetchPncpDetail(String detailUrl) throws Exception {
        if (isPncpHost(detailUrl)) {
            return fetchPncpDetailViaPlaywright(detailUrl);
        }
        // Fallback HTTP/regex para hosts nao-PNCP.
        var markdown = getMarkdownHttp(detailUrl);
        var matcher = PDF_PATTERN.matcher(markdown);
        if (!matcher.find()) {
            return null;
        }
        var pdfUrl = cleanUrl(matcher.group());
        return new PncpDetailResult(pdfUrl, ScraperUtilities.fileNameFromUrl(pdfUrl, "contrato.pdf"), markdown, "");
    }

    private List<String> discoverPncpDetailLinksViaPlaywright(String url) {
        var context = browserPool.getBrowser().newContext(new Browser.NewContextOptions()
            .setAcceptDownloads(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setViewportSize(1280, 800));
        try {
            var page = context.newPage();
            page.navigate(url);
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(PNCP_WAIT_TIMEOUT_MS));
            } catch (TimeoutError ex) {
                LOGGER.warn("pncp_lista_networkidle_timeout url={} (rede nao estabilizou em {} ms, prosseguindo)", url, PNCP_WAIT_TIMEOUT_MS);
            }
            // SPA Angular: apos NETWORKIDLE, aguarda o conteudo Angular renderizar antes de procurar links.
            // Sem este wait, o DOM ainda contem apenas o shell da SPA (CSS/fonts) sem os itens de contrato.
            page.waitForTimeout(3000);
            // Usa count() + locator (como o .NET) em vez de waitForSelector para evitar
            // TargetClosedError quando a pagina PNCP fecha/redireciona antes do seletor aparecer.
            int total;
            try {
                var items = page.locator("a.br-item[title='Acessar item.'][href^='/']");
                total = items.count();
            } catch (Exception ex) {
                LOGGER.warn("pncp_lista_locator_falhou url={}", url, ex);
                return List.of();
            }
            var links = new LinkedHashSet<String>();
            for (var i = 0; i < total; i++) {
                try {
                    var href = page.locator("a.br-item[title='Acessar item.'][href^='/']").nth(i).getAttribute("href");
                    if (href != null && !href.startsWith("javascript")) {
                        links.add(href.startsWith("/") ? "https://pncp.gov.br" + href : href);
                    }
                } catch (Exception ex) {
                    LOGGER.debug("pncp_lista_item_falhou url={} index={}", url, i, ex);
                }
            }
            if (links.isEmpty()) {
                try {
                    var content = page.content();
                    var sample = content == null ? "" : content.substring(0, Math.min(2000, content.length()));
                    LOGGER.warn("pncp_lista_sem_anchors url={} html_total_anchors={} html_size={} html_sample={}",
                        url, total, content == null ? 0 : content.length(), sample.replaceAll("\\s+", " "));
                } catch (Exception ex) {
                    LOGGER.warn("pncp_lista_sem_anchors url={} (nao foi possivel ler o DOM)", url, ex);
                }
            } else {
                LOGGER.info("pncp_lista_descobertos url={} total={}", url, links.size());
            }
            return new ArrayList<>(links);
        } catch (Exception ex) {
            LOGGER.error("pncp_lista_falhou url={}", url, ex);
            return List.of();
        } finally {
            context.close();
        }
    }

    private PncpDetailResult fetchPncpDetailViaPlaywright(String detailUrl) {
        var context = browserPool.getBrowser().newContext(new Browser.NewContextOptions()
            .setAcceptDownloads(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setViewportSize(1280, 800));
        try {
            var page = context.newPage();
            page.navigate(detailUrl);
            // Espelha o scraper .NET: a pagina de detalhe do PNCP e uma SPA Angular que carrega os dados do
            // contrato (e a secao de arquivos/download) por XHR assincrono. Esperar o NetworkIdle e o conteudo
            // do contrato renderizar ANTES de procurar o botao de download evita timeouts em massa.
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(PNCP_WAIT_TIMEOUT_MS));
            } catch (TimeoutError ex) {
                LOGGER.warn("pncp_detalhe_networkidle_timeout url={} (rede nao estabilizou em {} ms, prosseguindo)", detailUrl, PNCP_WAIT_TIMEOUT_MS);
            }
            // Aguarda o conteudo principal do contrato renderizar (h1) antes de interagir com as abas.
            try {
                page.waitForSelector(PNCP_CONTRATO_WAIT_SELECTOR, new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.ATTACHED)
                    .setTimeout(PNCP_WAIT_TIMEOUT_MS));
            } catch (TimeoutError ex) {
                LOGGER.warn("pncp_detalhe_sem_conteudo url={} (selector {} nao apareceu em {} ms)", detailUrl, PNCP_CONTRATO_WAIT_SELECTOR, PNCP_WAIT_TIMEOUT_MS);
            }
            // Aguarda tambem que o conteudo Angular esteja visivel (VISIBLE em vez de ATTACHED)
            // para garantir que a SPA terminou de renderizar antes de interagir com as abas.
            try {
                page.waitForSelector(PNCP_CONTRATO_WAIT_SELECTOR, new Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(PNCP_WAIT_TIMEOUT_MS));
            } catch (TimeoutError ex) {
                LOGGER.warn("pncp_detalhe_conteudo_nao_visivel url={} (selector {} ficou ATTACHED mas nunca VISIBLE)", detailUrl, PNCP_CONTRATO_WAIT_SELECTOR);
            }
            // Clica na aba "Arquivos" para renderizar o DOM da tab onde fica o botao de download.
            // O PNCP e uma SPA Angular: a[aria-label="Fazer download"] so existe no DOM apos o clique.
            // Sem este passo, todos os contratos retornam null por download nao encontrado.
            // Estrategia de clique em 3 camadas (fallback progressivo):
            //   1. Clica em qualquer tab/button com texto exato "Arquivos"
            //   2. Fallback: clica via JavaScript no elemento que tenha texto "Arquivos"
            //   3. Se nada funcionar, tenta acessar a URL diretamente com #arquivos
            var tabClicked = false;
            try {
                // Camada 1: localizar a aba "Arquivos" por seletor CSS + texto
                var arquivosTab = page.locator(PNCP_ARQUIVOS_TAB_SELECTOR)
                    .filter(new Locator.FilterOptions().setHasText("Arquivos"));
                if (arquivosTab.count() > 0) {
                    arquivosTab.first().click();
                    tabClicked = true;
                    LOGGER.debug("pncp_detalhe_aba_arquivos_clicada url={} (camada 1: seletor CSS)", detailUrl);
                }
            } catch (Exception ex) {
                LOGGER.debug("pncp_detalhe_aba_arquivos_camada1_falhou url={}", detailUrl, ex);
            }
            if (!tabClicked) {
                try {
                    // Camada 2: clique via JavaScript em qualquer elemento cujo texto visivel seja "Arquivos"
                    var clicked = (Boolean) page.evaluate("() => {"
                        + "const tabs = document.querySelectorAll('a, button, [role=tab]');"
                        + "for (const tab of tabs) {"
                        + "  if ((tab.textContent || '').trim() === 'Arquivos') {"
                        + "    tab.click(); return true;"
                        + "  }"
                        + "}"
                        + "return false;"
                        + "}");
                    if (Boolean.TRUE.equals(clicked)) {
                        tabClicked = true;
                        LOGGER.debug("pncp_detalhe_aba_arquivos_clicada url={} (camada 2: JS click)", detailUrl);
                    }
                } catch (Exception ex) {
                    LOGGER.debug("pncp_detalhe_aba_arquivos_camada2_falhou url={}", detailUrl, ex);
                }
            }
            if (tabClicked) {
                // Aguarda a SPA Angular renderizar o conteudo da tab (chamadas XHR + pintura do DOM).
                // PNCP e uma SPA Angular lenta — 2000ms era insuficiente para renderizar o conteudo da aba Arquivos.
                page.waitForTimeout(5000);
                try {
                    page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(PNCP_WAIT_TIMEOUT_MS));
                } catch (TimeoutError ex) {
                    LOGGER.debug("pncp_detalhe_networkidle_pos_clique_timeout url={}", detailUrl);
                }
                try {
                    page.waitForSelector(PNCP_DOWNLOAD_SELECTOR, new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.ATTACHED)
                        .setTimeout(PNCP_WAIT_TIMEOUT_MS));
                } catch (TimeoutError ex) {
                    LOGGER.warn("pncp_detalhe_sem_download_pos_clique url={} (selector {} nao apareceu apos clique na aba Arquivos)", detailUrl, PNCP_DOWNLOAD_SELECTOR);
                }
            } else {
                // Camada 3: se nao encontrou aba "Arquivos" via DOM, tenta navegar para a URL com hash
                var hashUrl = detailUrl.contains("#") ? detailUrl : detailUrl + "#arquivos";
                if (!hashUrl.equals(detailUrl)) {
                    try {
                        page.navigate(hashUrl);
                        page.waitForTimeout(2000);
                        LOGGER.debug("pncp_detalhe_navegou_hash url={}", hashUrl);
                        tabClicked = true;
                    } catch (Exception ex) {
                        LOGGER.debug("pncp_detalhe_hash_falhou url={}", detailUrl, ex);
                    }
                }
                if (!tabClicked) {
                    LOGGER.warn("pncp_detalhe_sem_aba_arquivos url={} (nenhuma das 3 camadas encontrou a aba Arquivos)", detailUrl);
                }
            }
            @SuppressWarnings("unchecked")
            var dados = (Map<String, Object>) page.evaluate("""
                () => {
                    // Estrategia 1: seletor exato do aria-label
                    let downloadAnchor = document.querySelector('a[aria-label="Fazer download"]');
                    // Estrategia 2: qualquer anchor com aria-label contendo "download"
                    if (!downloadAnchor) {
                        downloadAnchor = Array.from(document.querySelectorAll('a[aria-label]')).find(a => (a.getAttribute('aria-label') || '').toLowerCase().includes('download'));
                    }
                    // Estrategia 3: qualquer anchor com href contendo "download" ou ".pdf"
                    if (!downloadAnchor) {
                        downloadAnchor = Array.from(document.querySelectorAll('a[href]')).find(a => {
                            const href = (a.href || '').toLowerCase();
                            return href.includes('download') || href.includes('.pdf');
                        });
                    }
                    // Estrategia 4: botao com texto "Download" ou "Baixar"
                    if (!downloadAnchor) {
                        downloadAnchor = Array.from(document.querySelectorAll('a, button')).find(el => {
                            const text = (el.textContent || '').trim().toLowerCase();
                            return text === 'download' || text === 'baixar' || text.includes('fazer download');
                        });
                    }
                    const pdfUrl = downloadAnchor ? (downloadAnchor.href || downloadAnchor.getAttribute('href') || null) : null;
                    const contratoH1 = Array.from(document.querySelectorAll('h1')).find(h => (h.textContent || '').toLowerCase().includes('contrato'));
                    const contrato = contratoH1 ? contratoH1.textContent.trim() : null;
                    let dataAssinatura = null;
                    const dataStrong = Array.from(document.querySelectorAll('strong')).find(s => (s.textContent || '').toLowerCase().includes('data de assinatura'));
                    if (dataStrong && dataStrong.nextElementSibling) {
                        dataAssinatura = (dataStrong.nextElementSibling.textContent || '').trim();
                    }
                    return { pdfUrl: pdfUrl, contrato: contrato, dataAssinatura: dataAssinatura };
                }
                """);
            var pdfUrlRaw = dados.get("pdfUrl");
            if (pdfUrlRaw == null || pdfUrlRaw.toString().isBlank()) {
                try {
                    var content = page.content();
                    var sample = content == null ? "" : content.substring(0, Math.min(2000, content.length()));
                    LOGGER.warn("pncp_detalhe_download_sem_href url={} html_size={} html_sample={}", detailUrl, content == null ? 0 : content.length(), sample.replaceAll("\\s+", " "));
                } catch (Exception ex) {
                    LOGGER.warn("pncp_detalhe_download_sem_href url={} (nao foi possivel ler o DOM)", detailUrl, ex);
                }
                return null;
            }
            var pdfUrl = cleanUrl(absoluteUrl(detailUrl, pdfUrlRaw.toString()));
            var contratoRaw = dados.get("contrato");
            var numeroContrato = contratoRaw == null || contratoRaw.toString().isBlank()
                ? ScraperUtilities.fileNameFromUrl(detailUrl, "contrato")
                : contratoRaw.toString();
            if (contratoRaw == null || contratoRaw.toString().isBlank()) {
                LOGGER.warn("pncp_detalhe_h1_contrato_ausente url={} (prosseguindo, pois o download foi localizado)", detailUrl);
            }
            var dataAssinaturaRaw = dados.get("dataAssinatura");
            var dataAssinatura = dataAssinaturaRaw == null ? "" : dataAssinaturaRaw.toString();
            var nomeArquivo = ScraperUtilities.slugify(numeroContrato) + ".pdf";
            var descricao = numeroContrato + System.lineSeparator() + htmlToMarkdown(page.content(), detailUrl);
            return new PncpDetailResult(pdfUrl, nomeArquivo, descricao, dataAssinatura);
        } catch (Exception ex) {
            LOGGER.error("pncp_detalhe_falhou url={}", detailUrl, ex);
            return null;
        } finally {
            context.close();
        }
    }

    private static boolean isPncpHost(String url) {
        try {
            var host = URI.create(url).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).endsWith(PNCP_HOST_SUFFIX);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String htmlToMarkdown(String html, String baseUrl) {
        var links = new StringBuilder();
        var matcher = ANCHOR_PATTERN.matcher(html == null ? "" : html);
        while (matcher.find()) {
            var href = absoluteUrl(baseUrl, decodeHtml(matcher.group(1)));
            var text = TAG_PATTERN.matcher(matcher.group(2)).replaceAll(" ").replaceAll("\\s+", " ").trim();
            links.append('\n').append(text.isBlank() ? href : text).append(' ').append(href);
        }
        var text = (html == null ? "" : html)
            .replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>", "\n")
            .replaceAll("(?i)</li>", "\n");
        text = TAG_PATTERN.matcher(text).replaceAll(" ").replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n\\s+", "\n");
        return decodeHtml(text).trim() + links;
    }

    private static String absoluteUrl(String baseUrl, String href) {
        try {
            return URI.create(baseUrl).resolve(href).toString();
        } catch (IllegalArgumentException ex) {
            return href;
        }
    }

    private static String cleanUrl(String url) {
        return url.replaceAll("[)\\]>'\",;]+$", "");
    }

    private static String decodeHtml(String value) {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ");
    }
}
