package br.com.ecad.captacao.documentscraper;

record HybridScrapingResult(
    boolean sucesso,
    int itensProcessados,
    String resultado,
    int itensDescobertos,
    int itensFiltrados,
    int itensBaixados,
    int itensPersistidos) {

    HybridScrapingResult(boolean sucesso, int itensProcessados, String resultado) {
        this(sucesso, itensProcessados, resultado, 0, 0, 0, itensProcessados);
    }
}
