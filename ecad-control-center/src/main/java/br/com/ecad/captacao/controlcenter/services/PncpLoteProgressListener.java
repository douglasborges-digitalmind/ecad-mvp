package br.com.ecad.captacao.controlcenter.services;

import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;

interface PncpLoteProgressListener {
    default void onPlanejado(int fontesPlanejadas) {
    }

    default void onFonteProcessada(FonteCaptacao fonte, int fontesProcessadas, int comandosDisparados, int canaisUtilizados) {
    }

    static PncpLoteProgressListener noop() {
        return new PncpLoteProgressListener() {
        };
    }
}