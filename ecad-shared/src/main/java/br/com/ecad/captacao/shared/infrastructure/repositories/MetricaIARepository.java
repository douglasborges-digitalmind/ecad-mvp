package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;

public interface MetricaIARepository {
    void salvar(MetricaExecucaoIA metrica) throws IOException;

    List<MetricaExecucaoIA> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente,
        TipoEvidencia tipoDocumento, UUID idFonteCaptacao) throws IOException;
}
