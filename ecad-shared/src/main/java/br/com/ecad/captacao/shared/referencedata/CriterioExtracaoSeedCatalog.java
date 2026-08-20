package br.com.ecad.captacao.shared.referencedata;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.CriterioExtracao;
import br.com.ecad.captacao.shared.domain.enums.TipoDocumento;
import br.com.ecad.captacao.shared.prompts.ExtractionPrompts;

public final class CriterioExtracaoSeedCatalog {
    private static final Map<TipoDocumento, UUID> STABLE_IDS = Map.of(
        TipoDocumento.CONTRATO_MUSICAL, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567003"));

    private CriterioExtracaoSeedCatalog() {
    }

    public static List<CriterioExtracao> create() {
        return create(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static List<CriterioExtracao> create(OffsetDateTime timestamp) {
        return List.of(
            create(TipoDocumento.CONTRATO_MUSICAL, timestamp));
    }

    public static UUID getStableId(TipoDocumento tipoDocumento) {
        var stableId = STABLE_IDS.get(tipoDocumento);
        if (stableId == null) {
            throw new IllegalArgumentException("TipoDocumento sem seed canonico: " + tipoDocumento);
        }

        return stableId;
    }

    private static CriterioExtracao create(TipoDocumento tipoDocumento, OffsetDateTime timestamp) {
        var criterio = new CriterioExtracao();
        criterio.id = getStableId(tipoDocumento);
        criterio.tipoDocumento = tipoDocumento;
        criterio.instrucoesExtracaoIa = ExtractionPrompts.getGuidanceFor(tipoDocumento);
        criterio.criadoEm = timestamp;
        criterio.atualizadoEm = timestamp;
        return criterio;
    }
}