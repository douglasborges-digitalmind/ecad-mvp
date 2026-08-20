package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.domain.exceptions.BlobStorageException;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;
import org.springframework.stereotype.Component;

/**
 * Step 4: Move o blob de staging para produção.
 */
@Component
class BlobPromotionStep implements PipelineStep {

    private final BlobStorageService blobStorage;

    BlobPromotionStep(BlobStorageService blobStorage) {
        this.blobStorage = blobStorage;
    }

    @Override
    public void execute(PipelineContext ctx) throws BlobStorageException {
        ctx.urlProducao = blobStorage.moveToProduction(ctx.documento.urlStagingInterno());
    }

    @Override
    public void compensate(PipelineContext ctx) {
        if (ctx.urlProducao != null && !ctx.urlProducao.isBlank()) {
            try {
                blobStorage.delete(ctx.urlProducao);
            } catch (BlobStorageException e) {
                // Compensação best-effort — já logamos no DefaultProcessingPipeline
            }
        }
    }
}