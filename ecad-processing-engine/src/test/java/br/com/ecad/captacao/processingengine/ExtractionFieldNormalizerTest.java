package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExtractionFieldNormalizerTest {
    @Test
    void normalizeShouldDeduplicateInterpretesByComparisonKeyPreservingOrder() {
        var result = new ExtractionResult();
        result.interpretes = List.of(" banda açai ", "Banda Acai", "dj sol", "DJ Sol", " ");

        ExtractionFieldNormalizer.normalize(result);

        assertThat(result.interpretes).containsExactly("Banda Açai", "DJ Sol");
    }
}