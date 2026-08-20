package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;

public class LocalMetricaOperacionalRepository implements MetricaOperacionalRepository {
    private static final String COLLECTION_NAME = "metricas-operacionais";

    private final LocalJsonFileStore store;
    public LocalMetricaOperacionalRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public void salvar(MetricaExecucaoOperacional metrica) throws IOException {
        store.mutateCollection(COLLECTION_NAME, MetricaExecucaoOperacional.class, items -> {
            items.add(metrica);
            return true;
        });
    }

    @Override
    public List<MetricaExecucaoOperacional> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente,
        UUID idFonteCaptacao) throws IOException {
        return store.readCollection(COLLECTION_NAME, MetricaExecucaoOperacional.class).stream()
            .filter(item -> inicio == null || item.timestamp != null && !item.timestamp.isBefore(inicio))
            .filter(item -> fim == null || item.timestamp != null && !item.timestamp.isAfter(fim))
            .filter(item -> componente == null || item.componente == componente)
            .filter(item -> idFonteCaptacao == null || idFonteCaptacao.equals(item.idFonteCaptacao))
            .sorted(Comparator.comparing((MetricaExecucaoOperacional item) -> item.timestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
    }
}
