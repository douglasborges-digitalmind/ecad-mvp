package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobDownload;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;
import org.junit.jupiter.api.Test;

class ExtractionServiceTest {
    @Test
    void adaptaPromptAoTipoDocumentoEExigeSchemaCompleto() throws Exception {
        var ai = new FakeAiProviderChain("{\"evento_identificado\":false,\"interpretes\":[]}");
        var service = new ExtractionService(ai, contentReader("SHOW DA VIRADA em Salvador"), new InMemoryExtractionResultCache());

        var result = service.extract(criarDocumento(TipoEvidencia.CONTRATO_MUSICAL), criarCriterio(TipoDocumento.CONTRATO_MUSICAL));

        assertThat(result.status()).isEqualTo(ExtractionExecutionStatus.NO_EVENT);
        assertThat(ai.lastPrompt).contains("apresentação musical ao vivo");
        assertThat(ai.lastPrompt).contains("FORMATO DE SAÍDA OBRIGATÓRIO");
        assertThat(ai.lastPrompt).contains("\"evento_identificado\"");
        assertThat(ai.lastPrompt).contains("\"interpretes\": []");
        assertThat(ai.lastPrompt).contains("Nome de arquivo, caminho de blob, nome de pasta, URL");
        assertThat(ai.lastPrompt).contains("CONTEXTO EXTERNO NÃO PROBATÓRIO:");
        assertThat(ai.lastPrompt).contains("METADADOS NÃO PROBATÓRIOS:");
        assertThat(ai.lastPrompt).contains("MATERIAL PROBATÓRIO DO DOCUMENTO:");
    }

    @Test
    void orientaContratoMusicalAIgnorarMetadadosComoProvaDoEvento() throws Exception {
        var ai = new FakeAiProviderChain("{\"evento_identificado\":false,\"interpretes\":[]}");
        var service = new ExtractionService(ai, contentReader("CONTRATO ADMINISTRATIVO Nº 0062/2024"), new InMemoryExtractionResultCache());

        service.extract(criarDocumento(TipoEvidencia.CONTRATO_MUSICAL), criarCriterio(TipoDocumento.CONTRATO_MUSICAL));

        assertThat(ai.lastPrompt).contains("Se houver apenas número do contrato, órgão público, município, vigência genérica ou referências de captura, prefira false");
        assertThat(ai.lastPrompt).contains("se o documento só mostra \"Contrato nº 0062/2024\" e o nome da banda aparece apenas no nome do arquivo, no caminho ou em metadados, a banda NÃO pode ser extraída");
    }

    @Test
    void priorizaTrechosRelevantesQuandoConteudoForMuitoGrande() throws Exception {
        var ai = new FakeAiProviderChain("{\"evento_identificado\":false,\"interpretes\":[]}");
        var cabecalho = "DIÁRIO OFICIAL DO MUNICÍPIO";
        var irrelevante = String.join(System.lineSeparator(), java.util.Collections.nCopies(3000, "bloco administrativo genérico sem evidência musical"));
        var relevante = "EXTRATO DE INEXIGIBILIDADE PARA SHOW MUSICAL DA BANDA SOLAR EM 12/08/2026 ÀS 21:00.";
        var service = new ExtractionService(ai, contentReader(cabecalho + System.lineSeparator() + System.lineSeparator() + irrelevante + System.lineSeparator() + System.lineSeparator() + relevante), new InMemoryExtractionResultCache());

        service.extract(criarDocumento(TipoEvidencia.CONTRATO_MUSICAL), criarCriterio(TipoDocumento.CONTRATO_MUSICAL));

        assertThat(ai.lastPrompt).contains(cabecalho);
        assertThat(ai.lastPrompt).contains(relevante);
        assertThat(ai.lastPrompt).doesNotContain(String.join(System.lineSeparator(), java.util.Collections.nCopies(20, "bloco administrativo genérico sem evidência musical")));
    }

    @Test
    void reutilizaResultadoEmCachePorHashConteudoESemNovaChamadaAoProvider() throws Exception {
        var ai = new FakeAiProviderChain("{\"evento_identificado\":true,\"titulo\":\"Festival do Sol\",\"interpretes\":[\"Banda X\"]}");
        var service = new ExtractionService(ai, contentReader("Festival do Sol"), new InMemoryExtractionResultCache());
        var documento = criarDocumento(TipoEvidencia.CONTRATO_MUSICAL);
        var criterio = criarCriterio(TipoDocumento.CONTRATO_MUSICAL);

        var first = service.extract(documento, criterio);
        var second = service.extract(documento, criterio);

        assertThat(ai.callCount).isEqualTo(1);
        assertThat(first.status()).isEqualTo(ExtractionExecutionStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(ExtractionExecutionStatus.SUCCESS);
        assertThat(second.resultado().titulo).isEqualTo("Festival do Sol");
    }

    @Test
    void removeMarcadoresMarkdownDaRespostaDaIaAntesDeParsearJson() throws Exception {
        var ai = new FakeAiProviderChain("```json\n{\"evento_identificado\":true,\"titulo\":\"Show da Banda Solar\",\"interpretes\":[\"Banda Solar\"]}\n```");
        var service = new ExtractionService(ai, contentReader("Show da Banda Solar"), new InMemoryExtractionResultCache());
        var documento = criarDocumento(TipoEvidencia.CONTRATO_MUSICAL);
        var criterio = criarCriterio(TipoDocumento.CONTRATO_MUSICAL);

        var result = service.extract(documento, criterio);

        assertThat(result.status()).isEqualTo(ExtractionExecutionStatus.SUCCESS);
        assertThat(result.resultado().titulo).isEqualTo("Show da Banda Solar");
        assertThat(result.resultado().interpretes).containsExactly("Banda Solar");
    }

    @Test
    void removeMarcadoresMarkdownSemFechamentoDaRespostaDaIa() throws Exception {
        var ai = new FakeAiProviderChain("```json\n{\"evento_identificado\":true,\"titulo\":\"Festival Sem Fechamento\",\"interpretes\":[\"Artista X\"]}");
        var service = new ExtractionService(ai, contentReader("Festival Sem Fechamento"), new InMemoryExtractionResultCache());
        var documento = criarDocumento(TipoEvidencia.CONTRATO_MUSICAL);
        var criterio = criarCriterio(TipoDocumento.CONTRATO_MUSICAL);

        var result = service.extract(documento, criterio);

        assertThat(result.status()).isEqualTo(ExtractionExecutionStatus.SUCCESS);
        assertThat(result.resultado().titulo).isEqualTo("Festival Sem Fechamento");
        assertThat(result.resultado().interpretes).containsExactly("Artista X");
    }

    @Test
    void removeMarcadoresMarkdownSemEspecificadorDeLinguagem() throws Exception {
        var ai = new FakeAiProviderChain("```\n{\"evento_identificado\":true,\"titulo\":\"Show Sem Linguagem\",\"interpretes\":[\"Artista Y\"]}\n```");
        var service = new ExtractionService(ai, contentReader("Show Sem Linguagem"), new InMemoryExtractionResultCache());
        var documento = criarDocumento(TipoEvidencia.CONTRATO_MUSICAL);
        var criterio = criarCriterio(TipoDocumento.CONTRATO_MUSICAL);

        var result = service.extract(documento, criterio);

        assertThat(result.status()).isEqualTo(ExtractionExecutionStatus.SUCCESS);
        assertThat(result.resultado().titulo).isEqualTo("Show Sem Linguagem");
        assertThat(result.resultado().interpretes).containsExactly("Artista Y");
    }

    @Test
    void extraiJsonMesmoComTextoExplicativoAntesEDepois() throws Exception {
        var ai = new FakeAiProviderChain("Aqui está o resultado da extração:\n{\"evento_identificado\":true,\"titulo\":\"Show Com Texto\",\"interpretes\":[\"Banda Z\"]}\nEspero que isso ajude!");
        var service = new ExtractionService(ai, contentReader("Show Com Texto"), new InMemoryExtractionResultCache());
        var documento = criarDocumento(TipoEvidencia.CONTRATO_MUSICAL);
        var criterio = criarCriterio(TipoDocumento.CONTRATO_MUSICAL);

        var result = service.extract(documento, criterio);

        assertThat(result.status()).isEqualTo(ExtractionExecutionStatus.SUCCESS);
        assertThat(result.resultado().titulo).isEqualTo("Show Com Texto");
        assertThat(result.resultado().interpretes).containsExactly("Banda Z");
    }

    private static DocumentContentReader contentReader(String text) {
        return new DocumentContentReader(new BlobStorageService() {
            @Override
            public BlobDownload download(String blobUrl) {
                return new BlobDownload(text.getBytes(StandardCharsets.UTF_8), "text/plain");
            }

            @Override
            public String moveToProduction(String stagingUrl) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String uploadStaging(byte[] content, String stagingPath, String fileName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void delete(String blobUrl) {
                throw new UnsupportedOperationException();
            }
        });
    }

    private static DocumentoCapturado criarDocumento(TipoEvidencia tipo) {
        return new DocumentoCapturado(
            UUID.randomUUID(),
            "https://origem/documento",
            "https://blob/staging/documento.txt",
            "Fonte oficial da prefeitura de Salvador/BA.",
            "hash-123",
            UUID.randomUUID(),
            tipo,
            Map.of("cidade_fonte", "Salvador", "uf_fonte", "BA"),
            OffsetDateTime.parse("2024-01-02T03:04:05Z"));
    }

    private static CriterioExtracao criarCriterio(TipoDocumento tipo) {
        var criterio = new CriterioExtracao();
        criterio.tipoDocumento = tipo;
        criterio.instrucoesExtracaoIa = "Extraia apenas informacoes comprovadas no documento.";
        return criterio;
    }

    private static class FakeAiProviderChain implements AiProviderChain {
        final String responseContent;
        String lastPrompt = "";
        int callCount;

        FakeAiProviderChain(String responseContent) {
            this.responseContent = responseContent;
        }

        @Override
        public AiProviderExecution processar(String prompt, byte[] mediaBytes, String mimeType, TipoEvidencia tipoDocumento, UUID idFonteCaptacao) {
            lastPrompt = prompt;
            callCount++;
            return new AiProviderExecution(
                new AiResponse(responseContent, 10, 5, "fake-model", ProviderIA.GEMINI_NATIVO, BigDecimal.ZERO),
                List.<MetricaExecucaoIA>of());
        }
    }
}