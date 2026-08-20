package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;

public interface MetricaOperacionalRepository {
    void salvar(MetricaExecucaoOperacional metrica) throws IOException;

    List<MetricaExecucaoOperacional> listar(OffsetDateTime inicio, OffsetDateTime fim, ComponenteIA componente,
        UUID idFonteCaptacao) throws IOException;
}
