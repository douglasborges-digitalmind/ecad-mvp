package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.contracts.KeysMetadados;
import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import org.springframework.stereotype.Component;

@Component
class ExtractionResultEnricher {
    void enriquecer(DocumentoCapturado documento, ExtractionResult resultado, FonteCaptacao fonte) {
        if (resultado.municipio == null || resultado.municipio.isBlank()) {
            resultado.municipio = metadataValue(documento, KeysMetadados.MUNICIPIO, "cidade_fonte");
        }
        if ((resultado.municipio == null || resultado.municipio.isBlank()) && fonte != null) {
            resultado.municipio = municipioFromFonte(fonte);
        }
        if (resultado.uf == null || resultado.uf.isBlank()) {
            resultado.uf = metadataValue(documento, KeysMetadados.UF, "uf_fonte");
        }
        if ((resultado.uf == null || resultado.uf.isBlank()) && fonte != null && fonte.baseStoragePath != null) {
            var parts = fonte.baseStoragePath.replace('\\', '/').split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                resultado.uf = parts[0].trim().toUpperCase(java.util.Locale.ROOT);
            }
        }
    }

    private static String metadataValue(DocumentoCapturado documento, String key, String legacyKey) {
        if (documento.metadados() == null) {
            return null;
        }

        var value = documento.metadados().get(key);
        if (value == null || value.isBlank()) {
            value = documento.metadados().get(legacyKey);
        }
        return value == null || value.isBlank() ? null : value;
    }

    private static String municipioFromFonte(FonteCaptacao fonte) {
        if (fonte.nome != null && !fonte.nome.isBlank()) {
            return TextNormalization.normalizeWhitespace(fonte.nome);
        }
        if (fonte.baseStoragePath != null && fonte.baseStoragePath.contains("/")) {
            var parts = fonte.baseStoragePath.replace('\\', '/').split("/");
            return parts.length > 1 ? titleCase(parts[1].replace('_', ' ')) : null;
        }
        return null;
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var normalized = value.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
        for (var index = 0; index < normalized.length; index++) {
            if (!normalized[index].isBlank()) {
                normalized[index] = normalized[index].substring(0, 1).toUpperCase(java.util.Locale.ROOT) + normalized[index].substring(1);
            }
        }
        return String.join(" ", normalized);
    }
}
