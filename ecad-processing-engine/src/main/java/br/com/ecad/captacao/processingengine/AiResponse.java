package br.com.ecad.captacao.processingengine;

import java.math.BigDecimal;

import br.com.ecad.captacao.shared.domain.enums.ProviderIA;

record AiResponse(String content, int tokensInput, int tokensOutput, String model, ProviderIA provider, BigDecimal costUsd) {
}
