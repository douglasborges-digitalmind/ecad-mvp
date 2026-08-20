package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import br.com.ecad.captacao.shared.domain.exceptions.ExtractionException;
import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;
import br.com.ecad.captacao.shared.infrastructure.repositories.CriterioExtracaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 2: Busca critério de extração e executa a extração via IA.
 */
@Component
class ExtractionStep implements PipelineStep {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractionStep.class);

    private final CriterioExtracaoRepository criterios;
    private final ExtractionService extractionService;

    ExtractionStep(CriterioExtracaoRepository criterios, ExtractionService extractionService) {
        this.criterios = criterios;
        this.extractionService = extractionService;
    }

    @Override
    public void execute(PipelineContext ctx) throws ProcessingException {
        var doc = ctx.documento;
        var tipo = toTipoDocumento(doc);
        ctx.criterio = tryOrProcessing(() -> criterios.obterPorTipoDocumento(tipo), "obter criterio").orElse(null);
        if (ctx.criterio == null) {
            LOGGER.error("Criterio de extracao nao encontrado para tipo {}. Documento permanece em staging.", doc.tipo());
            throw new ExtractionException("Criterio de extracao nao encontrado para tipo " + doc.tipo());
        }

        ctx.extraction = tryOrProcessing(() -> extractionService.extract(doc, ctx.criterio), "extracao IA");
        LOGGER.debug("Extracao concluida: status={}", ctx.extraction.status());
    }

    private static TipoDocumento toTipoDocumento(br.com.ecad.captacao.shared.contracts.DocumentoCapturado doc) {
        return switch (doc.tipo()) {
            case CONTRATO_MUSICAL -> TipoDocumento.CONTRATO_MUSICAL;
        };
    }

    private static <T> T tryOrProcessing(java.util.concurrent.Callable<T> action, String label) throws ProcessingException {
        try {
            return action.call();
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessingException("Erro em " + label + ": " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}