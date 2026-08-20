package br.com.ecad.captacao.controlcenter;

import br.com.ecad.captacao.controlcenter.services.FonteCaptacaoService;
import br.com.ecad.captacao.shared.infrastructure.local.LocalDevelopmentSettings;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import br.com.ecad.captacao.shared.referencedata.CriterioExtracaoSeedCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class PncpContractSourcesBootstrap implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PncpContractSourcesBootstrap.class);

    private final FonteCaptacaoService fonteCaptacaoService;
    private final CriterioExtracaoRepository criterioExtracaoRepository;
    private final LocalDevelopmentSettings localDevelopmentSettings;

    PncpContractSourcesBootstrap(
        FonteCaptacaoService fonteCaptacaoService,
        CriterioExtracaoRepository criterioExtracaoRepository,
        LocalDevelopmentSettings localDevelopmentSettings) {
        this.fonteCaptacaoService = fonteCaptacaoService;
        this.criterioExtracaoRepository = criterioExtracaoRepository;
        this.localDevelopmentSettings = localDevelopmentSettings;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (localDevelopmentSettings.enabled) {
            return;
        }

        var fontes = fonteCaptacaoService.migrarFontesContratos();
        var criterios = CriterioExtracaoSeedCatalog.create();
        for (var criterio : criterios) {
            criterioExtracaoRepository.criar(criterio);
        }
        LOGGER.info(
            "Bootstrap Cosmos concluido: fontes PNCP total={}, criadas={}, atualizadas={}, inalteradas={}; criterios={}",
            fontes.processadas(), fontes.criadas(), fontes.atualizadas(), fontes.inalteradas(), criterios.size());
    }
}
