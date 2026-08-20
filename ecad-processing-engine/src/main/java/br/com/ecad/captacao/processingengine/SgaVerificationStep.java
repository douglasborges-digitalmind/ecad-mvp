package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;
import org.springframework.stereotype.Component;

/**
 * Step 3: Verifica se o evento já existe no SGA.
 */
@Component
class SgaVerificationStep implements PipelineStep {

    private final SgaClient sgaClient;

    SgaVerificationStep(SgaClient sgaClient) {
        this.sgaClient = sgaClient;
    }

    @Override
    public void execute(PipelineContext ctx) throws ProcessingException {
        var r = ctx.extraction.resultado();
        try {
            ctx.statusSga = sgaClient.verificar(
                text(r.titulo), text(r.local),
                r.dataInicio == null ? "" : r.dataInicio.toString(),
                r.dataTermino == null
                    ? (r.dataInicio == null ? "" : r.dataInicio.toString())
                    : r.dataTermino.toString(),
                text(r.uf));
        } catch (Exception e) {
            throw new ProcessingException("Falha ao verificar SGA", e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}