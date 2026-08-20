package br.com.ecad.captacao.controlcenter.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SetupPncpUrlsRequest(
    @JsonProperty("csv_path") String csvPath,
    @JsonProperty("output_path") String outputPath,
    @JsonProperty("csv_base64") String csvBase64,
    @JsonProperty("keywords") String keywords,
    @JsonProperty("rate_limit_seconds") Double rateLimitSeconds
) {
}