package br.com.ecad.captacao.shared.domain.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CriterioExtracao {
    @JsonProperty("id")
    public UUID id = UUID.randomUUID();

    @JsonProperty("instrucoes_extracao_ia")
    public String instrucoesExtracaoIa = "";

    @JsonProperty("tipoDocumento")
    public TipoDocumento tipoDocumento;

    @JsonProperty("criado_em")
    public OffsetDateTime criadoEm = OffsetDateTime.now();

    @JsonProperty("atualizado_em")
    public OffsetDateTime atualizadoEm = OffsetDateTime.now();
}
