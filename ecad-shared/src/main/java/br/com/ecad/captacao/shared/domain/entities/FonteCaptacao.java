package br.com.ecad.captacao.shared.domain.entities;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Fonte de captacao gerenciada pelo Control Center.
 * 
 * <p>O {@link #id} deve ser derivado deterministicamente do {@link #baseStoragePath}
 * via {@link #criarComIdDeterministico(String)}. Isso garante que recriacoes da
 * mesma fonte (ex: apos delete e redeploy) mantenham o mesmo UUID, evitando
 * documentos orfaos no pipeline de processamento.</p>
 */
public class FonteCaptacao {
    @JsonProperty("id")
    public UUID id = UUID.randomUUID();

    @JsonProperty("nome")
    public String nome = "";

    @JsonProperty("canais_scraping")
    public List<CanalDeScraping> canaisScraping = new ArrayList<>();

    @JsonProperty("metadados")
    public Map<String, String> metadados = new LinkedHashMap<>();

    @JsonProperty("criado_em")
    public OffsetDateTime criadoEm = OffsetDateTime.now();

    @JsonProperty("atualizado_em")
    public OffsetDateTime atualizadoEm = OffsetDateTime.now();

    @JsonProperty("unidade_ecad")
    public String unidadeEcad = "";

    @JsonProperty("base_storage_path")
    public String baseStoragePath = "";

    /**
     * Cria uma nova instancia com {@link #id} derivado deterministicamente
     * do {@code baseStoragePath} (RFC 4122 UUID v3 via namespace DNS).
     *
     * <p>O path e normalizado (trim, lowercase) antes da derivacao.
     * O mesmo path sempre produz o mesmo UUID.</p>
     *
     * @param baseStoragePath chave natural da fonte (ex: "RJ/TERESOPOLIS")
     * @return instancia com id deterministico
     */
    public static FonteCaptacao criarComIdDeterministico(String baseStoragePath) {
        var fonte = new FonteCaptacao();
        fonte.baseStoragePath = baseStoragePath;
        var canonical = baseStoragePath.trim().toLowerCase(Locale.ROOT);
        fonte.id = UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8));
        return fonte;
    }
}
