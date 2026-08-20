package br.com.ecad.captacao.shared.common;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Abstração para schedulers com single-flight (execução única concorrente).
 *
 * <p>Elimina a duplicação do padrão {@code AtomicBoolean + @Scheduled + try/finally}
 * replicado em múltiplos schedulers do projeto.
 *
 * <p>Uso:
 * <pre>{@code
 * @Service
 * class MeuScheduler extends SingleflightScheduler {
 *     MeuScheduler() {
 *         super("MeuScheduler");
 *     }
 *
 *     @Override
 *     protected void execute() throws Exception {
 *         // lógica do scheduler
 *     }
 *
 *     @Scheduled(fixedDelay = 60000)
 *     void runScheduled() {
 *         runSafely();
 *     }
 * }
 * }</pre>
 */
public abstract class SingleflightScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleflightScheduler.class);

    private final String schedulerName;
    private final AtomicBoolean running = new AtomicBoolean(false);

    protected SingleflightScheduler(String schedulerName) {
        this.schedulerName = schedulerName;
    }

    /**
     * Executa a lógica do scheduler com proteção single-flight e MDC de correlation_id.
     * Se já estiver em execução, loga um INFO e retorna imediatamente.
     */
    protected final void runSafely() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.info("{} já está em execução, ignorando disparo concorrente.", schedulerName);
            return;
        }

        try {
            MDC.put("correlation_id", "sched-" + UUID.randomUUID());
            execute();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("{} interrompido.", schedulerName, ex);
        } catch (Exception ex) {
            LOGGER.error("Falha na execução de {}.", schedulerName, ex);
        } finally {
            MDC.remove("correlation_id");
            running.set(false);
        }
    }

    /**
     * Lógica específica do scheduler a ser implementada pelas subclasses.
     *
     * @throws Exception se a execução falhar
     */
    protected abstract void execute() throws Exception;
}
