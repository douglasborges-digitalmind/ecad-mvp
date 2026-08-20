package br.com.ecad.captacao.shared.infrastructure.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import br.com.ecad.captacao.shared.domain.entities.Documento;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalCriterioExtracaoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalDocumentoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalEventoRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalMunicipioUnidadeRepository;
import br.com.ecad.captacao.shared.infrastructure.local.repositories.LocalSequencialRepository;
import br.com.ecad.captacao.shared.prompts.ExtractionPrompts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoriesTest {
    @TempDir
    Path tempDir;

    @Test
    void repositoriesShouldPersistCollectionsInLocalJsonStore() throws Exception {
        var store = new LocalJsonFileStore(new LocalDevelopmentSettings(tempDir, true));
        var eventos = new LocalEventoRepository(store);
        var documentos = new LocalDocumentoRepository(store);
        var sequenciais = new LocalSequencialRepository(store);

        var evento = new Evento(
            null, null, "Festival Local", OffsetDateTime.parse("2026-04-01T20:00:00Z"), null,
            "Praca Central", "Salvador", "BA", "Bahia", null, null, null, null, null, null, null, null, null,
            StatusEvento.AGENDADO, StatusSGA.INEDITO, NivelCompletude.BASICO, null, null, null, null, null, null
        );

        eventos.criar(evento);

        var documento = new Documento(
            null, "https://example.org/doc.pdf", "abc", null, null, null, null, null, null
        );
        documentos.salvar(documento);

        assertThat(eventos.buscarPorDedup("festival local", "praca central", evento.dataInicio(), "salvador", "ba")).isPresent();
        assertThat(eventos.listar("Salvador", StatusEvento.AGENDADO, StatusSGA.INEDITO, null, null, null, null, null)).hasSize(1);
        assertThat(documentos.urlJaFoiProcessada("https://example.org/doc.pdf")).isTrue();
        assertThat(documentos.arquivoJaFoiProcessado("ABC")).isTrue();
        assertThat(sequenciais.proximoSequencial(2026)).isEqualTo(1);
        assertThat(sequenciais.proximoSequencial(2026)).isEqualTo(2);
    }

    @Test
    void localJsonStoreShouldNotLeaveTemporaryFilesAfterWrite() throws Exception {
        var store = new LocalJsonFileStore(new LocalDevelopmentSettings(tempDir, true));
        var evento = new Evento(
            null, null, "Festival Local", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );

        store.writeCollection("eventos", java.util.List.of(evento));

        try (var stream = Files.list(tempDir.resolve("data"))) {
            assertThat(stream.map(path -> path.getFileName().toString()).toList())
                .containsExactly("eventos.json");
        }
    }

    @Test
    void municipioRepositoryShouldSeedReferenceCatalog() throws Exception {
        var store = new LocalJsonFileStore(new LocalDevelopmentSettings(tempDir, true));
        var municipios = new LocalMunicipioUnidadeRepository(store);

        assertThat(municipios.listarPorUf("BA")).isNotEmpty();
        assertThat(municipios.buscarPorUfMunicipio("ba", "salvador")).isPresent();
    }

    @Test
    void criterioRepositoryShouldSeedExtractionPrompts() throws Exception {
        var store = new LocalJsonFileStore(new LocalDevelopmentSettings(tempDir, true));
        var criterios = new LocalCriterioExtracaoRepository(store);

        var criterio = criterios.obterPorTipoDocumento(TipoDocumento.CONTRATO_MUSICAL);

        assertThat(criterio).isPresent();
        assertThat(criterio.orElseThrow().instrucoesExtracaoIa).isEqualTo(ExtractionPrompts.GUIDANCE_CONTRATO_MUSICAL);
        assertThat(ExtractionPrompts.getCoreSections(br.com.ecad.captacao.shared.domain.enums.TipoEvidencia.CONTRATO_MUSICAL)).hasSize(9);
    }
}