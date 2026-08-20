package br.com.ecad.captacao.processingengine;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.Documento;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Step 1: Persiste o documento capturado no repositório (upsert).
 * Best-effort: falha na persistência não bloqueia o pipeline.
 * Paridade com o C#, que não tem este passo — o scraper já persiste ao publicar.
 */
@Component
class PersistDocumentoStep implements PipelineStep {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistDocumentoStep.class);

    private final DocumentoRepository documentos;

    PersistDocumentoStep(DocumentoRepository documentos) {
        this.documentos = documentos;
    }

    @Override
    public void execute(PipelineContext ctx) {
        var doc = ctx.documento;
        var documento = new Documento(
            doc.id() != null ? doc.id() : UUID.randomUUID(),
            text(doc.urlOrigem()),
            text(doc.hashConteudo()),
            doc.idFonteCaptacao(),
            doc.tipo(),
            text(doc.urlStagingInterno()),
            doc.metadados() == null ? "" : text(doc.metadados().get("nome_arquivo")),
            doc.metadados() == null ? "" : text(doc.metadados().get("componente_origem")),
            doc.timestamp() == null ? OffsetDateTime.now() : doc.timestamp()
        );
        try {
            documentos.atualizar(documento);
            LOGGER.debug("Documento persistido: id={}", documento.id());
        } catch (Exception e) {
            // Best-effort: a persistencia do documento nao e pre-requisito para o pipeline.
            // O C# sequer tem este passo. Se falhar (CosmosException, IOException, etc),
            // logamos e prosseguimos — o scraper ja persiste o documento ao publicar.
            LOGGER.warn("Falha ao persistir documento (best-effort, pipeline continua). docId={} tipoExcecao={}",
                documento.id(), e.getClass().getSimpleName(), e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
