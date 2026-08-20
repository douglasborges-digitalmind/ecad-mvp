package br.com.ecad.captacao.controlcenter.models;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutarScrapingLotePncpAsyncStatus(
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("status") String status,
    @JsonProperty("criado_em") OffsetDateTime criadoEm,
    @JsonProperty("iniciado_em") OffsetDateTime iniciadoEm,
    @JsonProperty("finalizado_em") OffsetDateTime finalizadoEm,
    @JsonProperty("fontes_planejadas") int fontesPlanejadas,
    @JsonProperty("fontes_processadas") int fontesProcessadas,
    @JsonProperty("comandos_disparados") int comandosDisparados,
    @JsonProperty("canais_utilizados") int canaisUtilizados,
    @JsonProperty("ultima_fonte_processada") String ultimaFonteProcessada,
    @JsonProperty("erro") String erro,
    @JsonProperty("request") ExecutarScrapingLotePncpRequest request
) {
}