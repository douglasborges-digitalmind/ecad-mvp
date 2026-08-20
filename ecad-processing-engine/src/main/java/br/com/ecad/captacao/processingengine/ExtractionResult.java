package br.com.ecad.captacao.processingengine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso;
import br.com.ecad.captacao.shared.domain.enums.TipoMusica;
import com.fasterxml.jackson.annotation.JsonProperty;

class ExtractionResult {
    @JsonProperty("evento_identificado")
    public boolean eventoIdentificado;

    @JsonProperty("titulo")
    public String titulo;

    @JsonProperty("data_inicio")
    public LocalDate dataInicio;

    @JsonProperty("data_termino")
    public LocalDate dataTermino;

    @JsonProperty("local")
    public String local;

    @JsonProperty("municipio")
    public String municipio;

    @JsonProperty("uf")
    public String uf;

    @JsonProperty("hora")
    public String hora;

    @JsonProperty("interpretes")
    public List<String> interpretes = new ArrayList<>();

    @JsonProperty("tipo_musica")
    public TipoMusica tipoMusica;

    @JsonProperty("cobranca_ingresso")
    public CobrancaIngresso cobrancaIngresso;

    @JsonProperty("valor_ingresso")
    public BigDecimal valorIngresso;

    @JsonProperty("capacidade_publico")
    public Integer capacidadePublico;

    @JsonProperty("promotor_cnpj")
    public String promotorCnpj;

    @JsonProperty("promotor_nome")
    public String promotorNome;

    @JsonProperty("promotor_contato")
    public String promotorContato;

    @JsonProperty("observacoes_ia")
    public String observacoesIa;
}
