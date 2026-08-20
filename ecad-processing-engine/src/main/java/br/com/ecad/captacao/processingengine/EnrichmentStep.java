package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.contracts.KeysMetadados;
import br.com.ecad.captacao.shared.domain.exceptions.ProcessingException;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 2b: Enriquecimento pós-extração — resolve fonte de captação e aplica enricher.
 */
@Component
class EnrichmentStep implements PipelineStep {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnrichmentStep.class);

    private final FonteCaptacaoRepository fontes;
    private final ExtractionResultEnricher enricher;

    EnrichmentStep(FonteCaptacaoRepository fontes, ExtractionResultEnricher enricher) {
        this.fontes = fontes;
        this.enricher = enricher;
    }

    @Override
    public void execute(PipelineContext ctx) throws ProcessingException {
        var doc = ctx.documento;
        var resultado = ctx.extraction.resultado();
        if (precisaFonteParaEnriquecimento(doc, resultado)) {
            try {
                ctx.fonte = fontes.obterPorId(doc.idFonteCaptacao()).orElse(null);
            } catch (Exception e) {
                LOGGER.error("Erro ao buscar fonte de captacao: idFonteCaptacao={} documentoId={}", doc.idFonteCaptacao(), doc.id(), e);
                throw new ProcessingException("Erro ao buscar fonte de captacao", e);
            }
            if (ctx.fonte == null) {
                LOGGER.warn("Fonte de captacao nao encontrada (id nao existe no banco): idFonteCaptacao={} documentoId={}", doc.idFonteCaptacao(), doc.id());
            }
        }
        enricher.enriquecer(doc, resultado, ctx.fonte);
    }

    private static boolean precisaFonteParaEnriquecimento(
            br.com.ecad.captacao.shared.contracts.DocumentoCapturado doc, ExtractionResult resultado) {
        return precisaCampo(resultado.municipio, doc, KeysMetadados.MUNICIPIO, "cidade_fonte")
            || precisaCampo(resultado.uf, doc, KeysMetadados.UF, "uf_fonte");
    }

    private static boolean precisaCampo(String valorExtraido,
            br.com.ecad.captacao.shared.contracts.DocumentoCapturado doc, String chavePrincipal, String chaveLegada) {
        if (valorExtraido != null && !valorExtraido.isBlank()) return false;
        if (doc.metadados() == null) return true;
        var v = doc.metadados().get(chavePrincipal);
        if (v == null || v.isBlank()) v = doc.metadados().get(chaveLegada);
        return v == null || v.isBlank();
    }
}