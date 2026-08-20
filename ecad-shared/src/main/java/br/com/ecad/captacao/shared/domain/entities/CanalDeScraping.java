package br.com.ecad.captacao.shared.domain.entities;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.domain.valueobjects.FrequenciaScraping;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CanalDeScraping {
    @JsonProperty("id")
    public UUID id = UUID.randomUUID();

    @JsonProperty("url")
    public String url = "";

    @JsonProperty("instrucoes_scraping_ia")
    public String instrucoesScrapingIa = "";

    @JsonProperty("palavras_chaves_busca")
    public List<String> palavrasChavesBusca = new ArrayList<>();

    @JsonProperty("metadados")
    public Map<String, String> metadados = new LinkedHashMap<>();

    @JsonProperty("ultima_leitura")
    public OffsetDateTime ultimaLeitura;

    @JsonProperty("frequencia")
    public FrequenciaScraping frequencia = new FrequenciaScraping();

    @JsonProperty("criado_em")
    public OffsetDateTime criadoEm;

    @JsonProperty("atualizado_em")
    public OffsetDateTime atualizadoEm;

    @JsonProperty("ativo")
    public boolean ativo = true;

    @JsonProperty("tipo")
    public TipoCanal tipo;
}
