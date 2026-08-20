package br.com.ecad.captacao.controlcenter.services;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

import br.com.ecad.captacao.shared.contracts.ExecutarScraping;
import br.com.ecad.captacao.shared.domain.entities.CanalDeScraping;
import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import org.springframework.stereotype.Service;

@Service
public class AgendamentoService {
    private final FonteCaptacaoRepository repository;
    private final EventPublisher publisher;

    public AgendamentoService(FonteCaptacaoRepository repository, EventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public int executarScrapingsAgendados() throws IOException {
        var agora = OffsetDateTime.now(ZoneOffset.UTC);
        var fontes = repository.listarComScrapingsVencidos(agora);
        var totalPublicados = 0;

        for (var fonte : fontes) {
            var reservas = criarReservas(fonte, agora);
            if (reservas.isEmpty()) {
                continue;
            }

            for (var reserva : reservas) {
                reserva.canal.frequencia.proximaExecucao = reserva.novaProximaExecucao;
                reserva.canal.ultimaLeitura = agora;
                reserva.canal.atualizadoEm = agora;
            }

            try {
                fonte.atualizadoEm = agora;
                repository.atualizar(fonte);
            } catch (IOException ex) {
                restaurarReservas(fonte, reservas);
                continue;
            }

            var houveFalha = false;
            for (var reserva : reservas) {
                try {
                    publisher.publicarExecutarScraping(reserva.comando);
                    totalPublicados++;
                } catch (IOException ex) {
                    houveFalha = true;
                    restaurarReserva(reserva);
                }
            }

            if (houveFalha) {
                fonte.atualizadoEm = agora;
                repository.atualizar(fonte);
            }
        }

        return totalPublicados;
    }

    private static ArrayList<ReservaAgendamento> criarReservas(FonteCaptacao fonte, OffsetDateTime agora) {
        var reservas = new ArrayList<ReservaAgendamento>();
        for (var canal : fonte.canaisScraping) {
            if (!canal.ativo || canal.frequencia == null || canal.frequencia.proximaExecucao == null || canal.frequencia.proximaExecucao.isAfter(agora)) {
                continue;
            }

            reservas.add(new ReservaAgendamento(
                canal,
                canal.frequencia.proximaExecucao,
                canal.ultimaLeitura,
                canal.atualizadoEm,
                FonteCaptacaoService.criarComandoExecutarScraping(fonte, canal, null),
                canal.frequencia.calcularProximaExecucao(agora)));
        }
        return reservas;
    }

    private static void restaurarReservas(FonteCaptacao fonte, ArrayList<ReservaAgendamento> reservas) {
        for (var reserva : reservas) {
            restaurarReserva(reserva);
        }
        if (!reservas.isEmpty()) {
            fonte.atualizadoEm = reservas.getFirst().atualizadoEmOriginal;
        }
    }

    private static void restaurarReserva(ReservaAgendamento reserva) {
        reserva.canal.frequencia.proximaExecucao = reserva.proximaExecucaoOriginal;
        reserva.canal.ultimaLeitura = reserva.ultimaLeituraOriginal;
        reserva.canal.atualizadoEm = reserva.atualizadoEmOriginal;
    }

    private record ReservaAgendamento(
        CanalDeScraping canal,
        OffsetDateTime proximaExecucaoOriginal,
        OffsetDateTime ultimaLeituraOriginal,
        OffsetDateTime atualizadoEmOriginal,
        ExecutarScraping comando,
        OffsetDateTime novaProximaExecucao) {
    }
}
