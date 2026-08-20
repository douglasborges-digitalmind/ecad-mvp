package br.com.ecad.captacao.shared.contracts;

/**
 * Nomes de tópicos do Event Hub / Service Bus usados pelos consumers.
 */
public final class Topics {

    private Topics() {}

    /** Comandos de scraping enviados para o Document Scraper. */
    public static final String SCRAPING_COMMANDS = "scraping-commands";

    /** Documentos capturados prontos para processamento pelo Processing Engine. */
    public static final String CAPTURED_DOCUMENTS = "captured-documents";
}