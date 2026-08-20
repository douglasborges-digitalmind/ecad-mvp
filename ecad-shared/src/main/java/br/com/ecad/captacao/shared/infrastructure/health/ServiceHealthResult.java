package br.com.ecad.captacao.shared.infrastructure.health;

/**
 * Resultado padronizado de health check de um serviço.
 */
public record ServiceHealthResult(boolean healthy, String detail) {
}