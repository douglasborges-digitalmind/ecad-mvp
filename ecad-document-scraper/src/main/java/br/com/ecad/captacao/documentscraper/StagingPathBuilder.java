package br.com.ecad.captacao.documentscraper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;

final class StagingPathBuilder {
    private StagingPathBuilder() {
    }

    static String buildLocalStagingPath(ExecutarScraping comando) {
        if (comando.stagingPathStorage() != null && !comando.stagingPathStorage().isBlank()) {
            return trimSlashes(comando.stagingPathStorage());
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var tipo = comando.tipoAlvo() == null ? "desconhecido" : comando.tipoAlvo().jsonValue();
        return "staging-area/document-scraper/" + tipo + "/" + now.getYear() + "/" + String.format("%02d", now.getMonthValue()) + "/" + comando.idFonteCaptacao();
    }

    private static String trimSlashes(String value) {
        return value.replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}