package br.com.ecad.captacao.sgastatussync;

import br.com.ecad.captacao.shared.common.SingleflightScheduler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class SgaStatusSyncScheduler extends SingleflightScheduler {
    private static final long INTERVAL_MS = 28_800_000L;

    private final SgaStatusSyncRunner runner;

    SgaStatusSyncScheduler(SgaStatusSyncRunner runner) {
        super("SgaStatusSync");
        this.runner = runner;
    }

    @EventListener(ApplicationReadyEvent.class)
    void runOnStartup() {
        runSafely();
    }

    @Scheduled(fixedDelay = INTERVAL_MS)
    void runScheduled() {
        runSafely();
    }

    @Override
    protected void execute() throws Exception {
        runner.execute();
    }
}
