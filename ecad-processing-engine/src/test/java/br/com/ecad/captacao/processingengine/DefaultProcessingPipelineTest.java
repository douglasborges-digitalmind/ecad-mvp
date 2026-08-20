package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.entities.Documento;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.entities.MunicipioUnidade;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MunicipioUnidadeRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.SequencialRepository;
import br.com.ecad.captacao.shared.infrastructure.metrics.MetricsCollector;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobDownload;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

class DefaultProcessingPipelineTest {
    @Test
    void mantemBlobEmStagingQuandoNaoHouverCriterio() {
        var blob = new FakeBlobStorageService();
        var extraction = new StubExtractionService(null);
        var eventService = new RecordingEventoProcessingService(false);
        var pipeline = pipeline(new FakeCriterioExtracaoRepository(null), extraction, eventService, blob);

        assertThatThrownBy(() -> pipeline.processar(criarDocumento()))
            .isInstanceOf(br.com.ecad.captacao.shared.domain.exceptions.ExtractionException.class)
            .hasMessageContaining("Criterio de extracao nao encontrado");

        assertThat(blob.moveCalled).isFalse();
        assertThat(blob.deleteCalled).isFalse();
        assertThat(extraction.called).isFalse();
        assertThat(eventService.called).isFalse();
    }

    @Test
    void naoDescartaBlobQuandoRespostaDaIaForInvalida() {
        var blob = new FakeBlobStorageService();
        var extraction = new StubExtractionService(new ExtractionExecutionResult(
            new ExtractionResult(),
            List.of(),
            ExtractionExecutionStatus.INVALID_AI_RESPONSE,
            "Falha ao interpretar a resposta JSON da IA."));
        var eventService = new RecordingEventoProcessingService(false);
        var pipeline = pipeline(new FakeCriterioExtracaoRepository(criterio()), extraction, eventService, blob);

        assertThatThrownBy(() -> pipeline.processar(criarDocumento()))
            .isInstanceOf(br.com.ecad.captacao.shared.domain.exceptions.ExtractionException.class)
            .hasMessageContaining("resposta JSON");

        assertThat(blob.moveCalled).isFalse();
        assertThat(blob.deleteCalled).isFalse();
        assertThat(eventService.called).isFalse();
    }

    @Test
    void compensaBlobPromovidoQuandoPersistenciaFalhar() {
        var result = new ExtractionResult();
        result.eventoIdentificado = true;
        result.titulo = "Evento Teste";
        result.local = "Praca Central";
        result.dataInicio = LocalDate.parse("2026-04-05");
        result.municipio = "Salvador";
        result.uf = "BA";
        var blob = new FakeBlobStorageService();
        var extraction = new StubExtractionService(new ExtractionExecutionResult(result, List.of(), ExtractionExecutionStatus.SUCCESS, null));
        var eventService = new RecordingEventoProcessingService(true);
        var pipeline = pipeline(new FakeCriterioExtracaoRepository(criterio()), extraction, eventService, blob);

        assertThatThrownBy(() -> pipeline.processar(criarDocumento()))
            .isInstanceOf(br.com.ecad.captacao.shared.domain.exceptions.ProcessingException.class)
            .hasMessageContaining("Falha ao persistir evento");

        assertThat(blob.moveCalled).isTrue();
        assertThat(blob.deleteCalled).isTrue();
        assertThat(blob.lastDeletedUrl).isEqualTo(blob.productionUrl);
    }

    @Test
    void naoConsultaFonteQuandoDocumentoJaTrouxerMunicipioEUfNosMetadados() throws Exception {
        var result = new ExtractionResult();
        result.eventoIdentificado = true;
        result.titulo = "Evento Teste";
        var blob = new FakeBlobStorageService();
        var extraction = new StubExtractionService(new ExtractionExecutionResult(result, List.of(), ExtractionExecutionStatus.SUCCESS, null));
        var eventService = new RecordingEventoProcessingService(false);
        var fonteRepository = new FakeFonteCaptacaoRepository();
        var pipeline = pipeline(new FakeCriterioExtracaoRepository(criterio()), extraction, eventService, blob, fonteRepository);

        pipeline.processar(new DocumentoCapturado(
            UUID.randomUUID(),
            "https://origem/documento",
            "https://blob/staging/documento.pdf",
            "capturar",
            "hash-123",
            UUID.randomUUID(),
            TipoEvidencia.CONTRATO_MUSICAL,
            Map.of("MUNICIPIO", "Salvador", "UF", "BA"),
            OffsetDateTime.parse("2024-01-02T03:04:05Z")));

        assertThat(fonteRepository.obterPorIdCalls).isZero();
    }

    private static DefaultProcessingPipeline pipeline(
        CriterioExtracaoRepository criterios,
        StubExtractionService extraction,
        RecordingEventoProcessingService eventos,
        FakeBlobStorageService blob) {
        return pipeline(criterios, extraction, eventos, blob, new FakeFonteCaptacaoRepository());
    }

    private static DefaultProcessingPipeline pipeline(
        CriterioExtracaoRepository criterios,
        StubExtractionService extraction,
        RecordingEventoProcessingService eventos,
        FakeBlobStorageService blob,
        FakeFonteCaptacaoRepository fonteRepository) {
        var metricaOperacionalRepository = new FakeMetricaOperacionalRepository();
        var metricaIaRepository = new FakeMetricaIARepository();
        var enricher = new ExtractionResultEnricher();
        var linkFonteResolver = new LinkFonteResolver(blob);
        var registry = new SimpleMeterRegistry();
        SgaClient sgaStub = (titulo, local, dataInicio, dataFim, uf) -> StatusSGA.INEDITO;
        return new DefaultProcessingPipeline(
            new PersistDocumentoStep(new FakeDocumentoRepository()),
            new ExtractionStep(criterios, extraction),
            new EnrichmentStep(fonteRepository, enricher),
            new SgaVerificationStep(sgaStub),
            new BlobPromotionStep(blob),
            new EventPersistenceStep(eventos, linkFonteResolver),
            new MetricsStep(metricaIaRepository),
            new ProcessingOperationalMetricsService(metricaOperacionalRepository),
            blob,
            new MetricsCollector(registry, "processing-engine"));
    }

    private static CriterioExtracao criterio() {
        var criterio = new CriterioExtracao();
        criterio.tipoDocumento = TipoDocumento.CONTRATO_MUSICAL;
        criterio.instrucoesExtracaoIa = "extrair";
        return criterio;
    }

    private static DocumentoCapturado criarDocumento() {
        return new DocumentoCapturado(
            UUID.randomUUID(),
            "https://origem/documento",
            "https://blob/staging/documento.pdf",
            "capturar",
            "hash-123",
            UUID.randomUUID(),
            TipoEvidencia.CONTRATO_MUSICAL,
            Map.of(),
            OffsetDateTime.parse("2024-01-02T03:04:05Z"));
    }

    private static class StubExtractionService extends ExtractionService {
        final ExtractionExecutionResult result;
        boolean called;

        StubExtractionService(ExtractionExecutionResult result) {
            super((prompt, mediaBytes, mimeType, tipoDocumento, idFonteCaptacao) -> null,
                new DocumentContentReader(new FakeBlobStorageService()),
                new InMemoryExtractionResultCache());
            this.result = result;
        }

        @Override
        ExtractionExecutionResult extract(DocumentoCapturado documento, CriterioExtracao criterio) {
            called = true;
            return result;
        }
    }

    private static class RecordingEventoProcessingService extends EventoProcessingService {
        final boolean shouldThrow;
        boolean called;

        RecordingEventoProcessingService(boolean shouldThrow) {
            super(new FakeEventoRepository(), new FakeSequencialRepository(), new FakeMunicipioUnidadeRepository());
            this.shouldThrow = shouldThrow;
        }

        @Override
        Evento processar(DocumentoCapturado documento, ExtractionResult resultado, String urlArmazenamento, String linkFonte, StatusSGA statusSga) {
            called = true;
            if (shouldThrow) {
                throw new IllegalStateException("Falha simulada ao persistir evento.");
            }
            return new Evento(
                UUID.randomUUID(), "COD-001", "Teste", OffsetDateTime.now(), null,
                "Local", "Municipio", "UF", "Unidade", "10:00",
                null, null, null, List.of(), null, null, null, null,
                StatusEvento.AGENDADO, statusSga, NivelCompletude.BASICO, null,
                OffsetDateTime.now(), OffsetDateTime.now(), null, UUID.randomUUID(), List.of()
            );
        }
    }

    private static class FakeBlobStorageService implements BlobStorageService {
        boolean moveCalled;
        boolean deleteCalled;
        String productionUrl = "https://blob/producao/documento.pdf";
        String lastDeletedUrl;

        @Override
        public BlobDownload download(String blobUrl) {
            return new BlobDownload(new byte[0], "application/pdf");
        }

        @Override
        public String moveToProduction(String stagingUrl) {
            moveCalled = true;
            return productionUrl;
        }

        @Override
        public String uploadStaging(byte[] content, String stagingPath, String fileName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String blobUrl) {
            deleteCalled = true;
            lastDeletedUrl = blobUrl;
        }
    }

    private record FakeCriterioExtracaoRepository(CriterioExtracao criterio) implements CriterioExtracaoRepository {
        @Override
        public Optional<CriterioExtracao> obterPorTipoDocumento(TipoDocumento tipoDocumento) {
            return Optional.ofNullable(criterio);
        }

        @Override
        public CriterioExtracao criar(CriterioExtracao criterio) {
            return criterio;
        }

        @Override
        public CriterioExtracao atualizar(CriterioExtracao criterio) {
            return criterio;
        }
    }

    private static class FakeFonteCaptacaoRepository implements FonteCaptacaoRepository {
        int obterPorIdCalls;

        @Override
        public FonteCaptacao criar(FonteCaptacao fonte) {
            return fonte;
        }

        @Override
        public Optional<FonteCaptacao> obterPorId(UUID id) {
            obterPorIdCalls++;
            return Optional.empty();
        }

        @Override
        public List<FonteCaptacao> listar(String unidadeEcad, Boolean ativo) {
            return List.of();
        }

        @Override
        public FonteCaptacao atualizar(FonteCaptacao fonte) {
            return fonte;
        }

        @Override
        public void remover(UUID id, String unidadeEcad) {
        }

        @Override
        public List<FonteCaptacao> listarComScrapingsVencidos(OffsetDateTime dataReferencia) {
            return List.of();
        }
    }

    private static class FakeMetricaIARepository implements MetricaIARepository {
        final List<MetricaExecucaoIA> metricas = new ArrayList<>();

        @Override
        public void salvar(MetricaExecucaoIA metrica) {
            metricas.add(metrica);
        }

        @Override
        public List<MetricaExecucaoIA> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente, TipoEvidencia tipoDocumento, UUID idFonteCaptacao) {
            return metricas;
        }
    }

    private static class FakeMetricaOperacionalRepository implements MetricaOperacionalRepository {
        final List<MetricaExecucaoOperacional> metricas = new ArrayList<>();

        @Override
        public void salvar(MetricaExecucaoOperacional metrica) {
            metricas.add(metrica);
        }

        @Override
        public List<MetricaExecucaoOperacional> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente, UUID idFonteCaptacao) {
            return metricas;
        }
    }

    private static class FakeDocumentoRepository implements DocumentoRepository {
        @Override
        public boolean urlJaFoiProcessada(String url) {
            return false;
        }

        @Override
        public boolean arquivoJaFoiProcessado(String hashConteudo) {
            return false;
        }

        @Override
        public void salvar(Documento documento) {
        }

        @Override
        public void atualizar(Documento documento) {
        }
    }

    private static class FakeEventoRepository implements EventoRepository {
        @Override
        public Evento criar(Evento evento) {
            return evento;
        }

        @Override
        public Optional<Evento> obterPorId(UUID id, String municipio) {
            return Optional.empty();
        }

        @Override
        public Optional<Evento> buscarPorDedup(String titulo, String local, OffsetDateTime data, String municipio, String uf) {
            return Optional.empty();
        }

        @Override
        public Evento atualizar(Evento evento) {
            return evento;
        }

        @Override
        public List<Evento> listar(String municipio, StatusEvento status, StatusSGA statusSga, NivelCompletude nivelCompletude, OffsetDateTime dataInicio, OffsetDateTime dataTermino, String codigoEvento, String unidadeEcad) {
            return List.of();
        }

        @Override
        public List<Evento> listarParaPlanilha() {
            return List.of();
        }

        @Override
        public List<Evento> listarPorStatusSga(StatusSGA statusSga) {
            return List.of();
        }
    }

    private static class FakeSequencialRepository implements SequencialRepository {
        @Override
        public int proximoSequencial(int ano) {
            return 1;
        }
    }

    private static class FakeMunicipioUnidadeRepository implements MunicipioUnidadeRepository {
        @Override
        public Optional<MunicipioUnidade> buscarPorUfMunicipio(String uf, String municipio) throws IOException {
            return Optional.empty();
        }

        @Override
        public MunicipioUnidade criar(MunicipioUnidade municipioUnidade) {
            return municipioUnidade;
        }

        @Override
        public List<MunicipioUnidade> listarPorUf(String uf) {
            return List.of();
        }

        @Override
        public List<MunicipioUnidade> listarMunicipios() {
            return List.of();
        }
    }
}