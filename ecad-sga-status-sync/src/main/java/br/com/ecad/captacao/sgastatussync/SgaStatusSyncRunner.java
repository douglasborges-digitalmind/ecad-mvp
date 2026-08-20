package br.com.ecad.captacao.sgastatussync;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class SgaStatusSyncRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SgaStatusSyncRunner.class);

    private final SgaApiClient sgaClient;
    private final EventoRepository eventoRepository;
    private final SgaMunicipioCodeResolver municipioCodeResolver;
    private final SgaStatusSyncSettings settings;

    SgaStatusSyncRunner(
        SgaApiClient sgaClient,
        EventoRepository eventoRepository,
        SgaMunicipioCodeResolver municipioCodeResolver,
        SgaStatusSyncSettings settings) {
        this.sgaClient = sgaClient;
        this.eventoRepository = eventoRepository;
        this.municipioCodeResolver = municipioCodeResolver;
        this.settings = settings;
    }

    void execute() throws Exception {
        var start = System.nanoTime();
        if (!settings.sgaVerificationEnabled()) {
            LOGGER.info("SgaStatusSync - SGA Sync desativado, abortando execucao");
            return;
        }
        LOGGER.info("SgaStatusSync - iniciando reverificacao batch");
        var naoVerificados = eventoRepository.listarPorStatusSga(StatusSGA.NAO_VERIFICADO);
        var ineditos = eventoRepository.listarPorStatusSga(StatusSGA.INEDITO);
        var eventos = new ArrayList<Evento>();
        eventos.addAll(naoVerificados);
        eventos.addAll(ineditos);
        LOGGER.info("SgaStatusSync - {} eventos para verificar ({} NAO_VERIFICADO, {} INEDITO)", eventos.size(), naoVerificados.size(), ineditos.size());

        var jaCadastrado = new AtomicInteger(0);
        var inedito = new AtomicInteger(0);
        var naoVerificado = new AtomicInteger(0);
        var erros = new AtomicInteger(0);

        var concurrency = Math.max(1, settings.concurrency());
        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            var semaphore = new Semaphore(concurrency);
            for (var evento : eventos) {
                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            processarEvento(evento, jaCadastrado, inedito, naoVerificado, erros);
                        } finally {
                            semaphore.release();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        erros.incrementAndGet();
                    }
                });
            }
        }

        var elapsedMs = (System.nanoTime() - start) / 1_000_000;
        LOGGER.info("SgaStatusSync - concluido em {}ms | Processados={} JaCadastrado={} Inedito={} NaoVerificado={} Erros={}", 
            elapsedMs, eventos.size(), jaCadastrado.get(), inedito.get(), naoVerificado.get(), erros.get());
    }

    private void processarEvento(Evento evento, AtomicInteger jaCadastrado, AtomicInteger inedito, AtomicInteger naoVerificado, AtomicInteger erros) {
        try {
            if (isBlank(evento.titulo()) || evento.dataInicio() == null || isBlank(evento.municipio()) || isBlank(evento.uf())) {
                eventoRepository.atualizar(evento.comStatusSga(StatusSGA.NAO_VERIFICADO));
                naoVerificado.incrementAndGet();
                return;
            }
            var result = sgaClient.verificarEvento(new SgaEventQuery(
                evento.titulo(),
                evento.dataInicio().toLocalDate(),
                evento.municipio(),
                evento.uf(),
                municipioCodeResolver.resolve(evento.municipio(), evento.uf())));
            
            var updatedEvento = evento.comStatusSga(result.status());
            eventoRepository.atualizar(updatedEvento);
            
            switch (result.status()) {
                case JA_CADASTRADO -> jaCadastrado.incrementAndGet();
                case INEDITO -> inedito.incrementAndGet();
                case NAO_VERIFICADO -> naoVerificado.incrementAndGet();
            }
            LOGGER.info(
                "SgaStatusSync - Evento {} ({}): {} -> {} | candidatos={} cache={} pos={} score={}/{}",
                evento.codigoEvento(),
                evento.titulo(),
                evento.statusSga(),
                result.status(),
                result.candidatesCount(),
                result.fromCache(),
                result.match().candidatePosition(),
                result.match().titleScore(),
                result.match().municipioScore());
        } catch (Exception ex) {
            erros.incrementAndGet();
            LOGGER.error("SgaStatusSync - Erro ao processar evento {} ({}), mantendo status atual", evento.codigoEvento(), evento.id(), ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
