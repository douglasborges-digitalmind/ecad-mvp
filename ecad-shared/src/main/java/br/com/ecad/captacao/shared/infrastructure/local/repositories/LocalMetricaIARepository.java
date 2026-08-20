package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoIA;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaIARepository;

public class LocalMetricaIARepository implements MetricaIARepository {
    private static final String COLLECTION_NAME = "metricas-ia";

    private final LocalJsonFileStore store;
    public LocalMetricaIARepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public void salvar(MetricaExecucaoIA metrica) throws IOException {
        store.mutateCollection(COLLECTION_NAME, MetricaExecucaoIA.class, items -> {




            // Garantir idempotência: upsert por id
            var exists = false;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id.equals(metrica.id)) {
                    items.set(i, metrica);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                items.add(metrica);
            }
            return true;
        });
    }

    @Override
    public List<MetricaExecucaoIA> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente,
        TipoEvidencia tipoDocumento, UUID idFonteCaptacao) throws IOException {
        return store.readCollection(COLLECTION_NAME, MetricaExecucaoIA.class).stream()
            .filter(item -> inicio == null || item.timestamp != null && !item.timestamp.isBefore(inicio))
            .filter(item -> fim == null || item.timestamp != null && !item.timestamp.isAfter(fim))
            .filter(item -> componente == null || item.componente == componente)
            .filter(item -> tipoDocumento == null || item.tipoDocumento == tipoDocumento)
            .filter(item -> idFonteCaptacao == null || idFonteCaptacao.equals(item.idFonteCaptacao))
            .sorted(Comparator.comparing((MetricaExecucaoIA item) -> item.timestamp, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .toList();
    }
}
