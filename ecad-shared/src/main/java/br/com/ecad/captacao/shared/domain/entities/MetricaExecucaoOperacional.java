package br.com.ecad.captacao.shared.domain.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MetricaExecucaoOperacional {
    @JsonProperty("id")
    public UUID id = UUID.randomUUID();

    @JsonProperty("id_execucao")
    public UUID idExecucao = UUID.randomUUID();

    @JsonProperty("componente")
    public ComponenteIA componente;

    @JsonProperty("operacao")
    public String operacao = "";

    @JsonProperty("duracao_total_ms")
    public long duracaoTotalMs;

    @JsonProperty("sucesso")
    public boolean sucesso;

    @JsonProperty("resultado")
    public String resultado = "";

    @JsonProperty("itens_processados")
    public int itensProcessados;

    @JsonProperty("itens_descobertos")
    public int itensDescobertos;

    @JsonProperty("itens_filtrados")
    public int itensFiltrados;

    @JsonProperty("itens_baixados")
    public int itensBaixados;

    @JsonProperty("itens_persistidos")
    public int itensPersistidos;

    @JsonProperty("id_fonte_captacao")
    public UUID idFonteCaptacao;

    @JsonProperty("timestamp")
    public OffsetDateTime timestamp = OffsetDateTime.now();

    /**
     * Detalhe da falha quando {@link #sucesso} é {@code false}.
     * Contém a mensagem original da exceção para diagnóstico.
     * Ex: "Criterio de extracao nao encontrado para tipo CONTRATO_MUSICAL"
     * ou "Read timed out" (timeout do blob storage).
     */
    @JsonProperty("falha_detalhe")
    public String falhaDetalhe;
}