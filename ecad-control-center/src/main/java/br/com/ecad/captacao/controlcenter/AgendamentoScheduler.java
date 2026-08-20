package br.com.ecad.captacao.controlcenter;

import br.com.ecad.captacao.controlcenter.services.AgendamentoService;
import br.com.ecad.captacao.shared.common.SingleflightScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "CONTROL_CENTER_SCHEDULING_ENABLED", havingValue = "true", matchIfMissing = true)
class AgendamentoScheduler extends SingleflightScheduler {
    private final AgendamentoService service;

    AgendamentoScheduler(AgendamentoService service) {
        super("AgendamentoScheduler");
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${AGENDAMENTO_INTERVAL_MS:60000}")
    void executar() {
        runSafely();
    }

    @Override
    protected void execute() throws Exception {
        service.executarScrapingsAgendados();
    }
}
