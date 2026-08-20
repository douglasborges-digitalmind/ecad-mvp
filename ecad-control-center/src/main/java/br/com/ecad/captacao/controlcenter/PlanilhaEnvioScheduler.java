package br.com.ecad.captacao.controlcenter;

import br.com.ecad.captacao.controlcenter.services.PlanilhaService;
import br.com.ecad.captacao.shared.common.SingleflightScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "CONTROL_CENTER_SCHEDULING_ENABLED", havingValue = "true", matchIfMissing = true)
class PlanilhaEnvioScheduler extends SingleflightScheduler {
    private final PlanilhaService planilhaService;

    PlanilhaEnvioScheduler(PlanilhaService planilhaService) {
        super("PlanilhaEnvioScheduler");
        this.planilhaService = planilhaService;
    }

    @Scheduled(cron = "${PLANILHA_ENVIO_CRON:0 0 6 * * *}", zone = "UTC")
    void gerarEEnviar() {
        runSafely();
    }

    @Override
    protected void execute() throws Exception {
        var planilha = planilhaService.gerarPlanilha();
        planilhaService.enviarPorEmail(planilha);
    }
}