package br.com.ecad.captacao.controlcenter.services;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpAsyncResult;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpAsyncStatus;
import br.com.ecad.captacao.controlcenter.models.ExecutarScrapingLotePncpRequest;
import org.springframework.stereotype.Service;

@Service
public class PncpAsyncBatchJobService {
    private final FonteCaptacaoService fonteCaptacaoService;
    private final Map<UUID, JobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public PncpAsyncBatchJobService(FonteCaptacaoService fonteCaptacaoService) {
        this.fonteCaptacaoService = fonteCaptacaoService;
    }

    public ExecutarScrapingLotePncpAsyncResult iniciar(ExecutarScrapingLotePncpRequest request) {
        var effectiveRequest = request == null
            ? new ExecutarScrapingLotePncpRequest(null, null, null, null, null, null, null, null)
            : request;
        var jobId = UUID.randomUUID();
        var jobState = new JobState(jobId, effectiveRequest);
        jobs.put(jobId, jobState);

        executor.submit(() -> executarJob(jobState));

        return new ExecutarScrapingLotePncpAsyncResult(
            jobId,
            jobState.status,
            "/api/fontes/executar-lote-pncp/jobs/" + jobId);
    }

    public ExecutarScrapingLotePncpAsyncStatus obter(UUID jobId) {
        var jobState = jobs.get(jobId);
        if (jobState == null) {
            return null;
        }
        return jobState.toResponse();
    }

    private void executarJob(JobState jobState) {
        jobState.markRunning();
        try {
            fonteCaptacaoService.executarScrapingLotePncp(jobState.request, new PncpLoteProgressListener() {
                @Override
                public void onPlanejado(int fontesPlanejadas) {
                    jobState.setPlanejado(fontesPlanejadas);
                }

                @Override
                public void onFonteProcessada(br.com.ecad.captacao.shared.domain.entities.FonteCaptacao fonte, int fontesProcessadas, int comandosDisparados, int canaisUtilizados) {
                    jobState.updateProgress(fonte == null ? null : fonte.nome, fontesProcessadas, comandosDisparados, canaisUtilizados);
                }
            });
            jobState.markCompleted();
        } catch (Exception ex) {
            jobState.markFailed(ex);
        }
    }

    private static final class JobState {
        private final UUID jobId;
        private final ExecutarScrapingLotePncpRequest request;
        private final OffsetDateTime criadoEm;
        private volatile String status;
        private volatile OffsetDateTime iniciadoEm;
        private volatile OffsetDateTime finalizadoEm;
        private volatile int fontesPlanejadas;
        private volatile int fontesProcessadas;
        private volatile int comandosDisparados;
        private volatile int canaisUtilizados;
        private volatile String ultimaFonteProcessada;
        private volatile String erro;

        private JobState(UUID jobId, ExecutarScrapingLotePncpRequest request) {
            this.jobId = jobId;
            this.request = request;
            this.criadoEm = OffsetDateTime.now(ZoneOffset.UTC);
            this.status = "queued";
        }

        private synchronized void markRunning() {
            status = "running";
            iniciadoEm = OffsetDateTime.now(ZoneOffset.UTC);
        }

        private synchronized void setPlanejado(int fontesPlanejadas) {
            this.fontesPlanejadas = fontesPlanejadas;
        }

        private synchronized void updateProgress(String ultimaFonteProcessada, int fontesProcessadas, int comandosDisparados, int canaisUtilizados) {
            this.ultimaFonteProcessada = ultimaFonteProcessada;
            this.fontesProcessadas = fontesProcessadas;
            this.comandosDisparados = comandosDisparados;
            this.canaisUtilizados = canaisUtilizados;
        }

        private synchronized void markCompleted() {
            status = "completed";
            finalizadoEm = OffsetDateTime.now(ZoneOffset.UTC);
        }

        private synchronized void markFailed(Exception ex) {
            status = "failed";
            erro = ex.getMessage();
            finalizadoEm = OffsetDateTime.now(ZoneOffset.UTC);
        }

        private ExecutarScrapingLotePncpAsyncStatus toResponse() {
            return new ExecutarScrapingLotePncpAsyncStatus(
                jobId,
                status,
                criadoEm,
                iniciadoEm,
                finalizadoEm,
                fontesPlanejadas,
                fontesProcessadas,
                comandosDisparados,
                canaisUtilizados,
                ultimaFonteProcessada,
                erro,
                request);
        }
    }
}