package br.com.ecad.captacao.sgastatussync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import org.junit.jupiter.api.Test;

class SgaStatusSyncRunnerTest {
    @Test
    void updatesEligibleEventsAndKeepsIncompleteAsNaoVerificado() throws Exception {
        var complete = evento("EVT-1", StatusSGA.NAO_VERIFICADO, "Festival Jazz", OffsetDateTime.parse("2026-04-10T20:00:00Z"), "Campinas", "SP");
        var incomplete = evento("EVT-2", StatusSGA.INEDITO, "Sem data", null, "Campinas", "SP");
        var repository = new InMemoryEventoRepository(List.of(complete, incomplete));
        var runner = new SgaStatusSyncRunner(
            query -> new SgaVerificationResult(StatusSGA.JA_CADASTRADO, new SgaMatchResult(), 1, false),
            repository,
            new SgaMunicipioCodeResolver(settings(false)),
            settings(true));

        runner.execute();

        // O runner cria novas instancias via evento.comStatusSga(...) (record imutavel);
        // os originais nao sao mutados. As atualizacoes sao capturadas em repository.updated.
        var updatedComplete = repository.updated.stream().filter(e -> "EVT-1".equals(e.codigoEvento())).findFirst().orElseThrow();
        var updatedIncomplete = repository.updated.stream().filter(e -> "EVT-2".equals(e.codigoEvento())).findFirst().orElseThrow();
        assertThat(updatedComplete.statusSga()).isEqualTo(StatusSGA.JA_CADASTRADO);
        assertThat(updatedIncomplete.statusSga()).isEqualTo(StatusSGA.NAO_VERIFICADO);
        assertThat(repository.updated).hasSize(2);
    }

    /**
     * Constrói um {@link Evento} via construtor canonico do record.
     * <p>
     * O {@link SgaStatusSyncRunner} consulta apenas codigoEvento, statusSga, titulo, dataInicio,
     * municipio e uf; os demais 21 campos recebem defaults seguros (null / lista vazia / valores
     * canonicos) porque nao afetam o fluxo de verificacao SGA.
     */
    private static Evento evento(String codigo, StatusSGA status, String titulo, OffsetDateTime dataInicio, String municipio, String uf) {
        return new Evento(
            UUID.randomUUID(),
            codigo,
            titulo,
            dataInicio,
            null,
            "Local Teste",
            municipio,
            uf,
            "Unidade Teste",
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            StatusEvento.AGENDADO,
            status,
            NivelCompletude.BASICO,
            TipoCanal.AGREGADOR_GOV,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            UUID.randomUUID(),
            List.of()
        );
    }

    /**
     * Constrói um {@link SgaStatusSyncSettings} via construtor canonico do record.
     * <p>
     * 15 campos na ordem: sgaOAuthUrl, sgaBaseUrl, sgaClientId, sgaClientSecret, sgaAuthorization,
     * sgaUser, municipioCsvPath, sgaVerificationEnabled, sgaTimeoutSeconds, sgaMaxRetries,
     * rateLimitDelayMs, sgaResultLimit, concurrency, mongoConnectionString, mongoDatabaseName.
     */
    private static SgaStatusSyncSettings settings(boolean enabled) {
        return new SgaStatusSyncSettings(
            "", "", "", "", "", "", "", enabled, 5, 1, 0, 600, 4, "", "ecad-captacao");
    }

    private static final class InMemoryEventoRepository implements EventoRepository {
        private final List<Evento> eventos;
        private final List<Evento> updated = new ArrayList<>();

        private InMemoryEventoRepository(List<Evento> eventos) {
            this.eventos = new ArrayList<>(eventos);
        }

        @Override
        public Evento criar(Evento evento) {
            eventos.add(evento);
            return evento;
        }

        @Override
        public Optional<Evento> obterPorId(UUID id, String municipio) {
            return eventos.stream()
                .filter(evento -> evento.id().equals(id))
                .filter(evento -> municipio == null || municipio.equals(evento.municipio()))
                .findFirst();
        }

        @Override
        public Optional<Evento> buscarPorDedup(String titulo, String local, OffsetDateTime data, String municipio, String uf) {
            return Optional.empty();
        }

        @Override
        public Evento atualizar(Evento evento) {
            updated.add(evento);
            return evento;
        }

        @Override
        public List<Evento> listar(String municipio, StatusEvento status, StatusSGA statusSga, NivelCompletude nivelCompletude, OffsetDateTime dataInicio, OffsetDateTime dataTermino, String codigoEvento, String unidadeEcad) throws IOException {
            return List.of();
        }

        @Override
        public List<Evento> listarParaPlanilha() {
            return List.of();
        }

        @Override
        public List<Evento> listarPorStatusSga(StatusSGA statusSga) {
            return eventos.stream().filter(evento -> evento.statusSga() == statusSga).toList();
        }
    }
}
