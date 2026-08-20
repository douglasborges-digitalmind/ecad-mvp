package br.com.ecad.captacao.documentscraper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.ecad.captacao.shared.JsonDefaults;
import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.contracts.KeysMetadados;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.referencedata.PncpMunicipiosReferenceCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class ContratoMusicalHybridScraperMethod implements HybridScraperMethod {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContratoMusicalHybridScraperMethod.class);

    private final HybridBrowserService browser;
    private final HybridDocumentStore documentStore;

    ContratoMusicalHybridScraperMethod(HybridBrowserService browser, HybridDocumentStore documentStore) {
        this.browser = browser;
        this.documentStore = documentStore;
    }

    @Override
    public boolean canHandle(ExecutarScraping comando) {
        return comando.tipoAlvo() == TipoEvidencia.CONTRATO_MUSICAL;
    }

    @Override
    public HybridScrapingResult processar(ExecutarScraping comando) throws Exception {
        return processarViaPncp(comando);
    }

    private HybridScrapingResult processarViaPncp(ExecutarScraping comando) throws Exception {
        var palavras = normalizarPalavrasChave(comando.palavrasChavesBusca());
        if (palavras.isEmpty()) {
            return new HybridScrapingResult(true, 0, "no_keywords");
        }

        var baseSearchUrl = resolvePncpSearchUrl(comando);
        var detalhesProcessados = new LinkedHashSet<String>();
        var descobertos = 0;
        var filtrados = 0;
        var baixados = 0;
        var persistidos = 0;
        for (var palavra : palavras) {
            var searchUrl = baseSearchUrl + "&q=" + java.net.URLEncoder.encode(palavra, StandardCharsets.UTF_8);
            List<String> detailLinks;
            try {
                detailLinks = browser.discoverPncpDetailLinks(searchUrl);
            } catch (Exception ex) {
                LOGGER.warn("pncp_discover_links_falhou url={}", searchUrl, ex);
                continue;
            }
            for (var detailUrl : detailLinks) {
                if (!detalhesProcessados.add(detailUrl)) {
                    continue;
                }
                descobertos++;
                PncpDetailResult detail = null;
                try {
                    detail = browser.fetchPncpDetail(detailUrl);
                } catch (Exception ex) {
                    LOGGER.warn("pncp_fetch_detail_falhou url={}", detailUrl, ex);
                    continue;
                }
                if (detail == null) {
                    continue;
                }
                final var detailFinal = detail;
                filtrados++;
                try {
                    var pdfBytes = browser.download(detailFinal.pdfUrl());
                    baixados++;
                    var metadadosContrato = new HashMap<String, String>();
                    putIfPresent(metadadosContrato, KeysMetadados.DATA_ASSINATURA, detailFinal.dataAssinatura());
                    putIfPresent(metadadosContrato, KeysMetadados.LINK_ORIGEM, detailUrl);
                    if (documentStore.persistirDocumento(comando, detailFinal.pdfUrl(), detailFinal.nomeArquivo(), pdfBytes, TipoEvidencia.CONTRATO_MUSICAL, null, metadadosContrato)) {
                        persistidos++;
                    }
                } catch (Exception ex) {
                    LOGGER.warn("pncp_download_ou_persistencia_falhou url={}", detailFinal.pdfUrl(), ex);
                }
            }
        }
        var resultado = persistidos > 0 ? "sucesso" : (descobertos == 0 ? "no_detail_links" : (filtrados == 0 ? "no_matches" : "no_persistido"));
        return new HybridScrapingResult(true, persistidos, resultado, descobertos, filtrados, baixados, persistidos);
    }

    private static String resolvePncpSearchUrl(ExecutarScraping comando) {
        var metadata = comando.metadados();
        if (metadata == null) {
            return comando.urlAlvo();
        }

        var municipio = metadata.getOrDefault(KeysMetadados.MUNICIPIO, "");
        var uf = metadata.getOrDefault(KeysMetadados.UF, "");
        return PncpMunicipiosReferenceCatalog.tryResolve(municipio, uf)
            .map(PncpMunicipiosReferenceCatalog.PncpMunicipio::url)
            .orElse(comando.urlAlvo());
    }

    private static List<String> normalizarPalavrasChave(List<String> palavras) {
        if (palavras == null) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var palavra : palavras) {
            var normalized = palavra == null ? "" : palavra.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}