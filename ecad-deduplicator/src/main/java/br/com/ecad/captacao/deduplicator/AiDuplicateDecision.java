package br.com.ecad.captacao.deduplicator;

import br.com.ecad.captacao.shared.domain.enums.ProviderIA;

record AiDuplicateDecision(boolean duplicado, double confianca, String justificativa, ProviderIA provider) {
}
