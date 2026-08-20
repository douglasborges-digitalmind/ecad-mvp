package br.com.ecad.captacao.processingengine;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
class InMemoryExtractionResultCache implements ExtractionResultCache {
    private final ConcurrentHashMap<String, ExtractionExecutionResult> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<ExtractionExecutionResult> get(String cacheKey) {
        return Optional.ofNullable(cache.get(cacheKey));
    }

    @Override
    public void save(String cacheKey, ExtractionExecutionResult result) {
        cache.put(cacheKey, result);
    }
}
