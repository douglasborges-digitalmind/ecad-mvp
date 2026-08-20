package br.com.ecad.captacao.documentscraper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;

class NoOpMetricaIARepository implements MetricaIARepository {
    @Override
    public void salvar(MetricaExecucaoIA metrica) {
    }

    @Override
    public List<MetricaExecucaoIA> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente, TipoEvidencia tipoDocumento, UUID idFonteCaptacao) {
        return List.of();
    }
}