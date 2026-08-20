package br.com.ecad.captacao.controlcenter.models;

public record MigrarFontesContratosResult(
    int processadas,
    int criadas,
    int atualizadas,
    int inalteradas,
    String persistencia,
    String origemCatalogo
) {
}