package br.com.ecad.captacao.shared.contracts;

/**
 * Rotas e nomes de serviços usados para roteamento local (filas baseadas em
 * sistema de arquivos) e identificação de componentes. Centraliza os literais
 * para evitar inconsistências entre publishers e consumers.
 */
public final class Routes {

    private Routes() {}

    public static final String DOCUMENT_SCRAPER = "document-scraper";
    public static final String PROCESSING_ENGINE = "processing-engine";
    public static final String SGA_STATUS_SYNC = "sga-status-sync";
    public static final String DEDUPLICATOR = "deduplicator";
    public static final String CONTROL_CENTER = "control-center";
}