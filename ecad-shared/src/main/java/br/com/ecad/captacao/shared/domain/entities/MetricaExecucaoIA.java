package br.com.ecad.captacao.shared.domain.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.domain.enums.TipoOperacaoIA;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MetricaExecucaoIA {
    @JsonProperty("id")
    public UUID id = UUID.randomUUID();

    @JsonProperty("id_execucao")
    public UUID idExecucao;

    @JsonProperty("componente")
    public ComponenteIA componente;

    @JsonProperty("tipo_operacao")
    public TipoOperacaoIA tipoOperacao;

    @JsonProperty("tipo_documento")
    public TipoEvidencia tipoDocumento;

    @JsonProperty("modelo_utilizado")
    public String modeloUtilizado = "";

    @JsonProperty("provider")
    public ProviderIA provider;

    @JsonProperty("tokens_input")
    public int tokensInput;

    @JsonProperty("tokens_output")
    public int tokensOutput;

    @JsonProperty("custo_usd")
    public BigDecimal custoUsd;

    @JsonProperty("tamanho_input_chars")
    public int tamanhoInputChars;

    @JsonProperty("duracao_chamada_ms")
    public long duracaoChamadaMs;

    @JsonProperty("id_fonte_captacao")
    public UUID idFonteCaptacao;

    @JsonProperty("sucesso")
    public boolean sucesso;

    @JsonProperty("resultado_descarte")
    public Boolean resultadoDescarte;

    @JsonProperty("timestamp")
    public OffsetDateTime timestamp = OffsetDateTime.now();
}
