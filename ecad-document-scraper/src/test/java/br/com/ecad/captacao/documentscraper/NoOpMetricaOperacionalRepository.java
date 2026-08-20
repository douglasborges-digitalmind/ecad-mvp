package br.com.ecad.captacao.documentscraper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;

class NoOpMetricaOperacionalRepository implements MetricaOperacionalRepository {
    final List<MetricaExecucaoOperacional> saved = new ArrayList<>();

    @Override
    public void salvar(MetricaExecucaoOperacional metrica) {
        saved.add(metrica);
    }

    @Override
    public List<MetricaExecucaoOperacional> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente, UUID idFonteCaptacao) {
        return List.copyOf(saved);
    }
}
