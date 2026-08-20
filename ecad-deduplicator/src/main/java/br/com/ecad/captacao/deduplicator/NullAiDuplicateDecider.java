package br.com.ecad.captacao.deduplicator;

import br.com.ecad.captacao.shared.domain.entities.Evento;

enum NullAiDuplicateDecider implements AiDuplicateDecider {
    INSTANCE;

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AiDuplicateDecision decide(Evento left, Evento right, double heuristicScore) {
        return null;
    }
}
