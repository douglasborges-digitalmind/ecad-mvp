package br.com.ecad.captacao.controlcenter.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CriarDestinatarioRequest(
    @JsonProperty("nome") String nome,
    @JsonProperty("email") String email,
    @JsonProperty("whatsapp") String whatsapp
) {
}
