package br.com.ecad.captacao.shared.domain.entities;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Destinatario {
    @JsonProperty("id")
    public UUID id = UUID.randomUUID();

    @JsonProperty("nome")
    public String nome = "";

    @JsonProperty("email")
    public String email = "";

    @JsonProperty("whatsapp")
    public String whatsapp;
}
