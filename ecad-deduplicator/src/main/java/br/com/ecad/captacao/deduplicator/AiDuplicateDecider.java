package br.com.ecad.captacao.deduplicator;

import br.com.ecad.captacao.shared.domain.entities.Evento;
interface AiDuplicateDecider {
    boolean isEnabled();

    AiDuplicateDecision decide(Evento left, Evento right, double heuristicScore) throws Exception;
}
