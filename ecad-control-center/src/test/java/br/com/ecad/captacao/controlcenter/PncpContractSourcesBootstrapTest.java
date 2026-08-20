package br.com.ecad.captacao.controlcenter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;

import br.com.ecad.captacao.controlcenter.models.MigrarFontesContratosResult;
import br.com.ecad.captacao.controlcenter.services.FonteCaptacaoService;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.referencedata.CriterioExtracaoSeedCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class PncpContractSourcesBootstrapTest {
    private static final DefaultApplicationArguments NO_ARGS = new DefaultApplicationArguments(new String[0]);

    @Test
    void naoExecutaBootstrapEmDesenvolvimentoLocal() throws Exception {
        var fontes = mock(FonteCaptacaoService.class);
        var criterios = mock(CriterioExtracaoRepository.class);
        var bootstrap = new PncpContractSourcesBootstrap(
            fontes, criterios, new LocalDevelopmentSettings(Path.of("."), true));

        bootstrap.run(NO_ARGS);

        verify(fontes, never()).migrarFontesContratos();
        verify(criterios, never()).criar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void migraFontesEFazUpsertDosCriteriosCanonicosEmCloud() throws Exception {
        var fontes = mock(FonteCaptacaoService.class);
        var criterios = mock(CriterioExtracaoRepository.class);
        when(fontes.migrarFontesContratos()).thenReturn(new MigrarFontesContratosResult(3, 1, 1, 1, "mongo", "catalogo"));
        var bootstrap = new PncpContractSourcesBootstrap(
            fontes, criterios, new LocalDevelopmentSettings(Path.of("."), false));

        bootstrap.run(NO_ARGS);

        verify(fontes).migrarFontesContratos();
        verify(criterios, times(CriterioExtracaoSeedCatalog.create().size()))
            .criar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void propagaFalhaDoSeedEImpedeStartupParcial() throws Exception {
        var fontes = mock(FonteCaptacaoService.class);
        var criterios = mock(CriterioExtracaoRepository.class);
        when(fontes.migrarFontesContratos()).thenReturn(new MigrarFontesContratosResult(1, 1, 0, 0, "mongo", "catalogo"));
        doThrow(new IOException("cosmos indisponivel"))
            .when(criterios).criar(org.mockito.ArgumentMatchers.any());
        var bootstrap = new PncpContractSourcesBootstrap(
            fontes, criterios, new LocalDevelopmentSettings(Path.of("."), false));

        assertThrows(IOException.class, () -> bootstrap.run(NO_ARGS));
    }
}
