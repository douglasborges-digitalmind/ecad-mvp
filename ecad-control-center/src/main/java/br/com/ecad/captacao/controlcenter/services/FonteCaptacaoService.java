package br.com.ecad.captacao.controlcenter.services;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import br.com.ecad.captacao.controlcenter.models.AtualizarFonteRequest;
import br.com.ecad.captacao.controlcenter.models.CriarFonteRequest;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpRequest;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpResult;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingManualRequest;
import br.com.ecad.captacao.controlcenter.models.MigrarFontesContratosResult;
import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.contracts.KeysMetadados;
import br.com.ecad.captacao.shared.domain.entities.CanalDeScraping;
import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import br.com.ecad.captacao.shared.domain.entities.MunicipioUnidade;
import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.domain.enums.TipoFrequencia;
import br.com.ecad.captacao.shared.domain.valueobjects.FrequenciaScraping;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalFonteCaptacaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import br.com.ecad.captacao.shared.referencedata.MunicipioUnidadeReferenceCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FonteCaptacaoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FonteCaptacaoService.class);
    private static final int MAX_AI_SEARCH_MAX_RESULTS = 100;
    private static final int MAX_LOTE_COMANDOS = 1000;
    private static final int MAX_LOTE_FONTES_PNCP = 1000;

    private final FonteCaptacaoRepository repository;
    private final EventPublisher publisher;
    private final ObjectMapper objectMapper;

    public FonteCaptacaoService(FonteCaptacaoRepository repository, EventPublisher publisher, ObjectMapper objectMapper) {
        this.repository = repository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    public FonteCaptacao criar(CriarFonteRequest request) throws IOException {
        validarCriarRequest(request);

        var agora = OffsetDateTime.now(ZoneOffset.UTC);
        var fonte = FonteCaptacao.criarComIdDeterministico(request.baseStoragePath());
        fonte.nome = request.nome();
        fonte.unidadeEcad = request.unidadeEcad();
        fonte.metadados = normalizarMetadados(request.metadados());
        fonte.criadoEm = agora;
        fonte.atualizadoEm = agora;
        fonte.canaisScraping = request.canaisScraping().stream().map(canal -> mapearCanal(canal, agora, null)).toList();

        return repository.criar(fonte);
    }

    public FonteCaptacao obterPorId(UUID id) throws IOException {
        return repository.obterPorId(id).orElse(null);
    }

    public List<FonteCaptacao> listar(String unidadeEcad, Boolean ativo) throws IOException {
        return repository.listar(unidadeEcad, ativo);
    }

    public FonteCaptacao atualizar(UUID id, AtualizarFonteRequest request) throws IOException {
        var fonte = repository.obterPorId(id).orElseThrow(() -> new NoSuchElementException("FonteCaptacao " + id + " nao encontrada."));
        var agora = OffsetDateTime.now(ZoneOffset.UTC);

        if (request.unidadeEcad() != null && !request.unidadeEcad().equals(fonte.unidadeEcad)) {
            throw new IllegalStateException("Alteracao de UnidadeEcad nao e suportada (partition key). Remova e recrie a fonte.");
        }

        if (request.nome() != null) {
            fonte.nome = request.nome();
        }
        if (request.baseStoragePath() != null) {
            fonte.baseStoragePath = request.baseStoragePath();
        }
        if (request.metadados() != null) {
            fonte.metadados = normalizarMetadados(request.metadados());
        }
        if (request.canaisScraping() != null) {
            var existing = new LinkedHashMap<UUID, CanalDeScraping>();
            for (var canal : fonte.canaisScraping) {
                existing.put(canal.id, canal);
            }

            fonte.canaisScraping = request.canaisScraping().stream()
                .map(canal -> mapearCanal(canal, agora, canal.id() == null ? null : existing.get(canal.id())))
                .toList();
        }

        fonte.atualizadoEm = agora;
        return repository.atualizar(fonte);
    }

    public void remover(UUID id) throws IOException {
        var fonte = repository.obterPorId(id).orElseThrow(() -> new NoSuchElementException("FonteCaptacao " + id + " nao encontrada."));
        repository.remover(id, fonte.unidadeEcad);
    }

    public void executarScrapingManual(UUID idFonte, ExecutarScrapingManualRequest request) throws IOException {
        var fonte = repository.obterPorId(idFonte).orElseThrow(() -> new NoSuchElementException("FonteCaptacao " + idFonte + " nao encontrada."));
        for (var canal : fonte.canaisScraping.stream().filter(canal -> canal.ativo).toList()) {
            publisher.publicarExecutarScraping(criarComandoExecutarScraping(fonte, canal, request));
        }
    }

    public void executarScrapingContratoSemIa(UUID idFonte) throws IOException {
        var fonte = repository.obterPorId(idFonte).orElseThrow(() -> new NoSuchElementException("FonteCaptacao " + idFonte + " nao encontrada."));
        for (var canal : fonte.canaisScraping.stream()
            .filter(canal -> canal.ativo && canal.tipo == TipoCanal.AGREGADOR_GOV)
            .toList()) {
            publisher.publicarExecutarScraping(criarComandoExecutarScraping(fonte, canal, null));
        }
    }

    public ExecutarScrapingLotePncpResult executarScrapingLotePncp(ExecutarScrapingLotePncpRequest request) throws IOException {
        return executarScrapingLotePncp(request, PncpLoteProgressListener.noop());
    }

    public ExecutarScrapingLotePncpResult executarScrapingLotePncp(ExecutarScrapingLotePncpRequest request, PncpLoteProgressListener progressListener) throws IOException {
        var effectiveRequest = request == null
            ? new ExecutarScrapingLotePncpRequest(null, null, null, null, null, null, null, null)
            : request;
        var effectiveProgressListener = progressListener == null ? PncpLoteProgressListener.noop() : progressListener;
        var limiteEfetivo = obterLimiteLotePncp(effectiveRequest.limite());
        var offset = Math.max(effectiveRequest.offset() == null ? 0 : effectiveRequest.offset(), 0);
        var manualRequest = new ExecutarScrapingManualRequest(
            effectiveRequest.searchDateFrom(),
            effectiveRequest.searchDateTo(),
            effectiveRequest.searchMaxResults(),
            effectiveRequest.metadados());

        var fontesElegiveis = repository.listar(effectiveRequest.unidadeEcad(), true).stream()
            .filter(fonte -> isBlank(effectiveRequest.uf()) || effectiveRequest.uf().equalsIgnoreCase(obterUfFonte(fonte)))
            .filter(fonte -> fonte.canaisScraping != null && fonte.canaisScraping.stream().anyMatch(canal -> canal.ativo && canal.tipo == TipoCanal.AGREGADOR_GOV))
            .skip(offset)
            .limit(limiteEfetivo)
            .toList();
        effectiveProgressListener.onPlanejado(fontesElegiveis.size());

        var comandos = 0;
        var canaisUtilizados = 0;
        var fontesProcessadas = 0;
        LOGGER.info("executarScrapingLotePncp iniciado uf={} fontesElegiveis={}", effectiveRequest.uf(), fontesElegiveis.size());
        for (var fonte : fontesElegiveis) {
            for (var canal : fonte.canaisScraping) {
                if (!canal.ativo || canal.tipo != TipoCanal.AGREGADOR_GOV) {
                    continue;
                }

                publisher.publicarExecutarScraping(criarComandoExecutarScraping(fonte, canal, manualRequest));
                comandos++;
                canaisUtilizados++;
                LOGGER.info("Comando scraping publicado fonteId={} canalId={} uf={} comandos={}",
                    fonte.id, canal.id, obterUfFonte(fonte), comandos);
            }
            fontesProcessadas++;
            effectiveProgressListener.onFonteProcessada(fonte, fontesProcessadas, comandos, canaisUtilizados);
        }
        LOGGER.info("executarScrapingLotePncp concluido uf={} fontesProcessadas={} comandos={} canaisUtilizados={}",
            effectiveRequest.uf(), fontesProcessadas, comandos, canaisUtilizados);

        return new ExecutarScrapingLotePncpResult(fontesElegiveis.size(), comandos, canaisUtilizados);
    }

    private static String obterUfFonte(FonteCaptacao fonte) {
        var ufMetadados = getMetadata(fonte.metadados, KeysMetadados.UF);
        if (!isBlank(ufMetadados)) {
            return ufMetadados;
        }
        return extrairUfBaseStoragePath(fonte.baseStoragePath);
    }

    private static String getMetadata(Map<String, String> metadados, String key) {
        if (metadados == null) {
            return null;
        }
        return metadados.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().equalsIgnoreCase(key))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private static int obterLimiteLotePncp(Integer limite) {
        if (limite == null) {
            return MAX_LOTE_FONTES_PNCP;
        }
        if (limite <= 0) {
            throw new IllegalArgumentException("limite deve ser maior que zero.");
        }
        if (limite > MAX_LOTE_FONTES_PNCP) {
            throw new IllegalArgumentException("limite deve ser menor ou igual a " + MAX_LOTE_FONTES_PNCP + ".");
        }
        return limite;
    }

    public MigrarFontesContratosResult migrarFontesContratos() throws IOException {
        var catalogo = lerMunicipiosContratos();
        var dados = catalogo.dados();
        var existentes = new ArrayList<>(repository.listar(null, null));
        var criadas = 0;
        var atualizadas = 0;
        var inalteradas = 0;
        for (var entry : dados.entrySet()) {
            var url = entry.getValue();
            if (isBlank(url) || "-".equals(url.trim())) {
                continue;
            }

            var request = criarFonteContrato(entry.getKey(), url, null);
            var existente = encontrarFonteContratoExistente(existentes, request);
            if (existente == null) {
                existentes.add(criar(request));
                criadas++;
                continue;
            }

            if (fonteContratoEquivalente(existente, request)) {
                inalteradas++;
                continue;
            }

            atualizar(existente.id, criarAtualizacaoFonteContrato(existente, request));
            atualizadas++;
        }

        return new MigrarFontesContratosResult(
            criadas + atualizadas + inalteradas,
            criadas,
            atualizadas,
            inalteradas,
            descreverPersistencia(),
            catalogo.origem());
    }

    public static ExecutarScraping criarComandoExecutarScraping(FonteCaptacao fonte, CanalDeScraping canal, ExecutarScrapingManualRequest request) {
        var tipoAlvo = mapearTipoAlvo(canal.tipo);
        var stagingPath = fonte.baseStoragePath + "/staging/" + canal.tipo.jsonValue() + "/" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(OffsetDateTime.now(ZoneOffset.UTC));
        var metadados = mergeMetadados(fonte, canal, request);
        return new ExecutarScraping(
            canal.url,
            tipoAlvo,
            canal.instrucoesScrapingIa == null ? "" : canal.instrucoesScrapingIa,
            canal.palavrasChavesBusca == null ? List.of() : canal.palavrasChavesBusca,
            stagingPath,
            fonte.id,
            canal.id,
            metadados);
    }

    private static TipoEvidencia mapearTipoAlvo(TipoCanal tipo) {
        return switch (tipo) {
            case AGREGADOR_GOV -> TipoEvidencia.CONTRATO_MUSICAL;
        };
    }

    private static CanalDeScraping mapearCanal(CriarFonteRequest.CriarCanalRequest request, OffsetDateTime agora, CanalDeScraping canalExistente) {
        var frequencia = new FrequenciaScraping();
        frequencia.tipo = request.frequencia().tipo();
        frequencia.diasDaSemana = request.frequencia().diasDaSemana() == null ? List.of() : request.frequencia().diasDaSemana();
        frequencia.horario = request.frequencia().horario();
        frequencia.intervaloHoras = request.frequencia().intervaloHoras();
        frequencia.proximaExecucao = canalExistente != null && frequenciaEquivalente(canalExistente.frequencia, frequencia)
            ? canalExistente.frequencia.proximaExecucao
            : frequencia.calcularProximaExecucao(agora);

        var canal = new CanalDeScraping();
        canal.id = canalExistente == null ? (request.id() == null ? UUID.randomUUID() : request.id()) : canalExistente.id;
        canal.url = request.url();
        canal.instrucoesScrapingIa = request.instrucoesScrapingIa() == null ? "" : request.instrucoesScrapingIa();
        canal.palavrasChavesBusca = request.palavrasChavesBusca() == null ? new ArrayList<>() : request.palavrasChavesBusca();
        canal.metadados = normalizarMetadados(request.metadados());
        canal.tipo = request.tipo();
        canal.ativo = canalExistente == null || canalExistente.ativo;
        canal.criadoEm = canalExistente == null ? agora : canalExistente.criadoEm;
        canal.atualizadoEm = agora;
        canal.ultimaLeitura = canalExistente == null ? null : canalExistente.ultimaLeitura;
        canal.frequencia = frequencia;
        return canal;
    }

    private static boolean frequenciaEquivalente(FrequenciaScraping atual, FrequenciaScraping nova) {
        return atual != null
            && atual.tipo == nova.tipo
            && java.util.Objects.equals(atual.horario, nova.horario)
            && java.util.Objects.equals(atual.intervaloHoras, nova.intervaloHoras)
            && atual.diasDaSemana.equals(nova.diasDaSemana);
    }

    private static Map<String, String> mergeMetadados(FonteCaptacao fonte, CanalDeScraping canal, ExecutarScrapingManualRequest request) {
        var merged = normalizarMetadados(fonte.metadados);
        merged.putAll(normalizarMetadados(canal.metadados));
        if (request != null) {
            merged.putAll(normalizarMetadados(request.metadados()));
        }
        merged.put(KeysMetadados.TIPO_CANAL, canal.tipo.jsonValue());
        merged.put(KeysMetadados.UNIDADE_ECAD, fonte.unidadeEcad);
        if (!isBlank(fonte.nome)) {
            merged.putIfAbsent(KeysMetadados.MUNICIPIO, fonte.nome.trim());
        }
        var ufFonte = extrairUfBaseStoragePath(fonte.baseStoragePath);
        if (!isBlank(ufFonte)) {
            merged.putIfAbsent(KeysMetadados.UF, ufFonte);
        }
        applyManualExecutionOverrides(merged, request);
        return merged;
    }

    private static String extrairUfBaseStoragePath(String baseStoragePath) {
        if (isBlank(baseStoragePath)) {
            return null;
        }

        var normalized = baseStoragePath.replace('\\', '/').trim();
        var separatorIndex = normalized.indexOf('/');
        var uf = separatorIndex >= 0 ? normalized.substring(0, separatorIndex) : normalized;
        return isBlank(uf) ? null : uf.trim().toUpperCase(Locale.ROOT);
    }

    private static void applyManualExecutionOverrides(Map<String, String> metadados, ExecutarScrapingManualRequest request) {
        if (request == null) {
            return;
        }
        if (!isBlank(request.searchDateFrom())) {
            metadados.put(KeysMetadados.SEARCH_DATE_FROM, LocalDate.parse(request.searchDateFrom()).toString());
        }
        if (!isBlank(request.searchDateTo())) {
            metadados.put(KeysMetadados.SEARCH_DATE_TO, LocalDate.parse(request.searchDateTo()).toString());
        }
        if (request.searchMaxResults() != null) {
            validarSearchMaxResults(request.searchMaxResults());
            metadados.put(KeysMetadados.SEARCH_MAX_RESULTS, request.searchMaxResults().toString());
        }
    }

    private static void validarSearchMaxResults(int searchMaxResults) {
        if (searchMaxResults <= 0 || searchMaxResults > MAX_AI_SEARCH_MAX_RESULTS) {
            throw new IllegalArgumentException(KeysMetadados.SEARCH_MAX_RESULTS + " deve estar entre 1 e " + MAX_AI_SEARCH_MAX_RESULTS + ".");
        }
    }

    private static final String MUNICIPIOS_CLASSPATH_RESOURCE =
        "br/com/ecad/captacao/shared/referencedata/municipiosPNCP.json";

    private MunicipiosContratosCatalogo lerMunicipiosContratos() throws IOException {
        var path = Path.of("municipiosPNCP.json").toAbsolutePath().normalize();
        if (Files.isRegularFile(path)) {
            return new MunicipiosContratosCatalogo(
                objectMapper.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, String>>() {
                }),
                "filesystem:" + path);
        }

        var classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(MUNICIPIOS_CLASSPATH_RESOURCE)) {
            if (stream != null) {
                return new MunicipiosContratosCatalogo(
                    objectMapper.readValue(stream, new TypeReference<LinkedHashMap<String, String>>() {
                    }),
                    "classpath:" + MUNICIPIOS_CLASSPATH_RESOURCE);
            }
        }

        try (InputStream stream = classLoader.getResourceAsStream("municipiosPNCP.json")) {
            if (stream == null) {
                throw new IllegalStateException(
                    "Arquivo municipiosPNCP.json nao encontrado em " + MUNICIPIOS_CLASSPATH_RESOURCE
                        + " nem na raiz do classpath.");
            }

            return new MunicipiosContratosCatalogo(
                objectMapper.readValue(stream, new TypeReference<LinkedHashMap<String, String>>() {
                }),
                "classpath:municipiosPNCP.json");
        }
    }

    private String descreverPersistencia() {
        if (repository instanceof LocalFonteCaptacaoRepository) {
            return "local";
        }
        return repository.getClass().getSimpleName();
    }

    private record MunicipiosContratosCatalogo(Map<String, String> dados, String origem) {
    }

    private static CriarFonteRequest criarFonteContrato(String municipioUf, String url, UUID canalId) {
        var cidadeEstado = municipioUf.split("/", -1);
        if (cidadeEstado.length != 2 || isBlank(cidadeEstado[0]) || isBlank(cidadeEstado[1])) {
            throw new IllegalArgumentException("Entrada invalida em municipiosPNCP.json: " + municipioUf);
        }

        var cidade = cidadeEstado[0].trim();
        var uf = cidadeEstado[1].trim().toUpperCase(Locale.ROOT);
        var frequencia = new CriarFonteRequest.FrequenciaRequest(TipoFrequencia.SEMANAL, List.of("segunda"), "16:00", null);
        var canal = new CriarFonteRequest.CriarCanalRequest(
            canalId,
            url.trim(),
            "",
            List.of("show", "contratação musical", "apresentação musical", "contratação artística", "apresentação artística"),
            Map.of(),
            TipoCanal.AGREGADOR_GOV,
            frequencia);

        return new CriarFonteRequest(
            cidade,
            obterNomeEstado(uf).toUpperCase(Locale.ROOT),
            uf + "/" + cidade.toUpperCase(Locale.ROOT).replace(" ", "_"),
            Map.of(),
            List.of(canal));
    }

    private static FonteCaptacao encontrarFonteContratoExistente(List<FonteCaptacao> existentes, CriarFonteRequest request) {
        var desiredBaseStoragePath = safe(request.baseStoragePath());
        var desiredNome = safe(request.nome());
        var desiredUnidadeEcad = safe(request.unidadeEcad());

        for (var fonte : existentes) {
            if (encontrarCanalContrato(fonte) == null) {
                continue;
            }

            if (desiredBaseStoragePath.equalsIgnoreCase(safe(fonte.baseStoragePath))) {
                return fonte;
            }

            if (desiredNome.equalsIgnoreCase(safe(fonte.nome)) && desiredUnidadeEcad.equalsIgnoreCase(safe(fonte.unidadeEcad))) {
                return fonte;
            }
        }

        return null;
    }

    private static boolean fonteContratoEquivalente(FonteCaptacao existente, CriarFonteRequest request) {
        if (!safe(existente.nome).equalsIgnoreCase(safe(request.nome()))) {
            return false;
        }
        if (!safe(existente.unidadeEcad).equalsIgnoreCase(safe(request.unidadeEcad()))) {
            return false;
        }
        if (!safe(existente.baseStoragePath).equalsIgnoreCase(safe(request.baseStoragePath()))) {
            return false;
        }

        var canalExistente = encontrarCanalContrato(existente);
        var canalDesejado = request.canaisScraping().getFirst();
        if (canalExistente == null) {
            return false;
        }

        return safe(canalExistente.url).equals(safe(canalDesejado.url()))
            && safe(canalExistente.instrucoesScrapingIa).equals(safe(canalDesejado.instrucoesScrapingIa()))
            && normalizarLista(canalExistente.palavrasChavesBusca).equals(normalizarLista(canalDesejado.palavrasChavesBusca()))
            && canalExistente.tipo == canalDesejado.tipo()
            && canalExistente.frequencia != null
            && canalExistente.frequencia.tipo == canalDesejado.frequencia().tipo()
            && normalizarLista(canalExistente.frequencia.diasDaSemana).equals(normalizarLista(canalDesejado.frequencia().diasDaSemana()))
            && safe(canalExistente.frequencia.horario).equals(safe(canalDesejado.frequencia().horario()))
            && java.util.Objects.equals(canalExistente.frequencia.intervaloHoras, canalDesejado.frequencia().intervaloHoras());
    }

    private AtualizarFonteRequest criarAtualizacaoFonteContrato(FonteCaptacao existente, CriarFonteRequest request) {
        var canalExistente = encontrarCanalContrato(existente);
        var canalDesejado = request.canaisScraping().getFirst();

        return new AtualizarFonteRequest(
            request.nome(),
            request.unidadeEcad(),
            request.baseStoragePath(),
            request.metadados(),
            List.of(new CriarFonteRequest.CriarCanalRequest(
                canalExistente == null ? null : canalExistente.id,
                canalDesejado.url(),
                canalDesejado.instrucoesScrapingIa(),
                canalDesejado.palavrasChavesBusca(),
                canalDesejado.metadados(),
                canalDesejado.tipo(),
                canalDesejado.frequencia())));
    }

    private static CanalDeScraping encontrarCanalContrato(FonteCaptacao fonte) {
        if (fonte.canaisScraping == null) {
            return null;
        }

        return fonte.canaisScraping.stream()
            .filter(canal -> canal.tipo == TipoCanal.AGREGADOR_GOV)
            .findFirst()
            .orElse(null);
    }

    private static List<String> normalizarLista(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream().map(FonteCaptacaoService::safe).toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String obterNomeEstado(String uf) {
        return switch (uf.toUpperCase(Locale.ROOT)) {
            case "AC" -> "Acre";
            case "AL" -> "Alagoas";
            case "AP" -> "Amapa";
            case "AM" -> "Amazonas";
            case "BA" -> "Bahia";
            case "CE" -> "Ceara";
            case "DF" -> "Distrito Federal";
            case "ES" -> "Espirito Santo";
            case "GO" -> "Goias";
            case "MA" -> "Maranhao";
            case "MT" -> "Mato Grosso";
            case "MS" -> "Mato Grosso do Sul";
            case "MG" -> "Minas Gerais";
            case "PA" -> "Para";
            case "PB" -> "Paraiba";
            case "PR" -> "Parana";
            case "PE" -> "Pernambuco";
            case "PI" -> "Piaui";
            case "RJ" -> "Rio de Janeiro";
            case "RN" -> "Rio Grande do Norte";
            case "RS" -> "Rio Grande do Sul";
            case "RO" -> "Rondonia";
            case "RR" -> "Roraima";
            case "SC" -> "Santa Catarina";
            case "SP" -> "Sao Paulo";
            case "SE" -> "Sergipe";
            case "TO" -> "Tocantins";
            default -> "Estado invalido";
        };
    }

    private static Map<String, String> normalizarMetadados(Map<String, String> metadados) {
        var normalized = new LinkedHashMap<String, String>();
        if (metadados == null) {
            return normalized;
        }
        metadados.forEach((key, value) -> {
            if (!isBlank(key) && !isBlank(value)) {
                normalized.put(key.trim().toUpperCase(java.util.Locale.ROOT), value.trim());
            }
        });
        return normalized;
    }

    private static void validarCriarRequest(CriarFonteRequest request) {
        if (request == null || isBlank(request.nome())) {
            throw new IllegalArgumentException("Nome e obrigatorio");
        }
        if (isBlank(request.unidadeEcad())) {
            throw new IllegalArgumentException("UnidadeEcad e obrigatoria");
        }
        if (isBlank(request.baseStoragePath())) {
            throw new IllegalArgumentException("BaseStoragePath e obrigatorio");
        }
        if (request.canaisScraping() == null || request.canaisScraping().isEmpty()) {
            throw new IllegalArgumentException("Ao menos um canal de scraping e obrigatorio");
        }
        for (var canal : request.canaisScraping()) {
            if (isBlank(canal.url())) {
                throw new IllegalArgumentException("URL do canal e obrigatoria");
            }
            URI.create(canal.url());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}