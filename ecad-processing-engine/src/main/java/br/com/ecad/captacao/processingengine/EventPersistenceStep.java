package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;
import org.springframework.stereotype.Component;

/**
 * Step 5: Persiste o evento extraído e resolve o link da fonte.
 */
@Component
class EventPersistenceStep implements PipelineStep {

    private final EventoProcessingService eventoProcessingService;
    private final LinkFonteResolver linkFonteResolver;

    EventPersistenceStep(EventoProcessingService eventoProcessingService, LinkFonteResolver linkFonteResolver) {
        this.eventoProcessingService = eventoProcessingService;
        this.linkFonteResolver = linkFonteResolver;
    }

    @Override
    public void execute(PipelineContext ctx) throws ProcessingException {
        var doc = ctx.documento;
        ctx.linkFonte = linkFonteResolver.resolve(doc, ctx.urlProducao);
        try {
            eventoProcessingService.processar(doc, ctx.extraction.resultado(),
                ctx.urlProducao, ctx.linkFonte, ctx.statusSga);
        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ProcessingException("Falha ao persistir evento", e);
        }
    }
}