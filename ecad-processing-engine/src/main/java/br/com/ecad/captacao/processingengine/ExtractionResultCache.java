package br.com.ecad.captacao.processingengine;

import java.util.Optional;

interface ExtractionResultCache {
    Optional<ExtractionExecutionResult> get(String cacheKey);

    void save(String cacheKey, ExtractionExecutionResult result);
}
