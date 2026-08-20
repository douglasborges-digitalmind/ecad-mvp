package br.com.ecad.captacao.processingengine;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.MetricaExecucaoOperacional;
import br.com.ecad.captacao.shared.domain.enums.ComponenteIA;
import br.com.ecad.captacao.shared.infrastructure.repositories.MetricaOperacionalRepository;
import org.springframework.stereotype.Service;

@Service
class ProcessingOperationalMetricsService {
    private final MetricaOperacionalRepository repository;

    ProcessingOperationalMetricsService(MetricaOperacionalRepository repository) {
        this.repository = repository;
    }

    void salvarResultadoPipeline(UUID idExecucao, UUID idFonteCaptacao, String resultado, boolean sucesso, int itensProcessados, long duracaoMs, String falhaDetalhe) throws Exception {
        var metrica = new MetricaExecucaoOperacional();
        metrica.idExecucao = idExecucao;
        metrica.idFonteCaptacao = idFonteCaptacao;
        metrica.componente = ComponenteIA.PROCESSING_ENGINE;
        metrica.operacao = "processing_pipeline";
        metrica.resultado = resultado;
        metrica.sucesso = sucesso;
        metrica.itensProcessados = itensProcessados;
        metrica.duracaoTotalMs = duracaoMs;
        metrica.falhaDetalhe = falhaDetalhe;
        metrica.timestamp = OffsetDateTime.now();
        repository.salvar(metrica);
    }
}