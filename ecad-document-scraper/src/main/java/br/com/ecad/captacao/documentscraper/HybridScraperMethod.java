package br.com.ecad.captacao.documentscraper;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;

interface HybridScraperMethod {
    boolean canHandle(ExecutarScraping comando);

    HybridScrapingResult processar(ExecutarScraping comando) throws Exception;
}
