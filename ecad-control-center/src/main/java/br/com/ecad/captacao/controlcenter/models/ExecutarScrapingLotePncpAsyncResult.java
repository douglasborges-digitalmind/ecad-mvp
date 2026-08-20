package br.com.ecad.captacao.controlcenter.models;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExecutarScrapingLotePncpAsyncResult(
    @JsonProperty("job_id") UUID jobId,
    @JsonProperty("status") String status,
    @JsonProperty("status_url") String statusUrl
) {
}