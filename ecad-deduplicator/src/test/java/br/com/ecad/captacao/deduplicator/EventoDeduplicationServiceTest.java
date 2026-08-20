package br.com.ecad.captacao.deduplicator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.entities.Evidencia;
import br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.ProviderIA;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.domain.enums.TipoMusica;
import org.junit.jupiter.api.Test;

class EventoDeduplicationServiceTest {
    private static final DeduplicationSettings SETTINGS = new DeduplicationSettings(
        Path.of("eventos.json"),
        true,
        false,
        0.82d,
        0.62d,
        List.of("hash", "url", "title", "title_city", "title_date", "city_date", "title_city_date", "local_city", "promotor_date", "cnpj"),
        List.of(),
        "",
        "google/gemini-3.1-flash-lite-preview",
        "https://openrouter.ai/api/v1",
        "",
        "gemini-3.1-flash-lite-preview",
        "https://generativelanguage.googleapis.com/v1beta",
        "llama3:8b",
        "http://localhost:11434",
        "",
        "",
        "",
        "");

    @Test
    void mergesComplementaryEvents() throws Exception {
        var eventoA = event("2026-00010", "Festival do Rio", "2026-05-10T20:00:00Z", "Praca Central", "Rio Branco", "AC", null, null, List.of())
            .comEvidenciaAdicionada(evidence(TipoEvidencia.CONTRATO_MUSICAL, "https://origem/a", "hash-a"));
        var eventoB = event("2026-00011", "Festival do Rio", "2026-05-10T20:00:00Z", "Praca Central", "Rio Branco", "AC", "2026-05-11T20:00:00Z", "Prefeitura de Rio Branco", List.of("Banda Aurora"))
            .comEvidenciaAdicionada(evidence(TipoEvidencia.CONTRATO_MUSICAL, "https://origem/b", "hash-b"));

        var result = service(SETTINGS).deduplicate(List.of(eventoA, eventoB));

        assertThat(result.eventos()).hasSize(1);
        var finalEvent = result.eventos().getFirst();
        assertThat(finalEvent.codigoEvento()).isEqualTo("2026-00010");
        assertThat(finalEvent.promotorNome()).isEqualTo("Prefeitura de Rio Branco");
        assertThat(finalEvent.interpretes()).contains("Banda Aurora");
        assertThat(finalEvent.evidencias()).hasSize(2);
        assertThat(result.mergedGroups()).hasSize(1);
    }

    @Test
    void doesNotMergeOverviewWithSpecificEvent() throws Exception {
        var calendario = event("2026-00020", "Calendario de Eventos 2026", null, null, "Cruzeiro do Sul", "AC", null, null, List.of("Banda Rainha Musical"));
        var evento = event("2026-00021", "Baile com Animacao Banda Rainha Musical", "2026-01-07T20:00:00Z", "Sociedade de Sao Rafael", "Cruzeiro do Sul", "AC", null, null, List.of("Banda Rainha Musical"));

        var result = service(SETTINGS).deduplicate(List.of(calendario, evento));

        assertThat(result.eventos()).hasSize(2);
        assertThat(result.mergedGroups()).isEmpty();
    }

    @Test
    void doesNotMergeSameEvidenceWhenMunicipioConflicts() throws Exception {
        var eventoA = event("2026-02207", "Festival da Juventude", "2026-01-18T20:00:00Z", null, "Caruaru", "PE", null, null, List.of())
            .comEvidenciaAdicionada(evidence(TipoEvidencia.CONTRATO_MUSICAL, "https://origem/evidencia-compartilhada", "hash-compartilhado"));
        var eventoB = event("2026-02208", "Festival da Juventude", "2026-01-18T20:00:00Z", null, "Recife", "PE", null, null, List.of())
            .comEvidenciaAdicionada(evidence(TipoEvidencia.CONTRATO_MUSICAL, "https://origem/evidencia-compartilhada", "hash-compartilhado"));

        var result = service(SETTINGS).deduplicate(List.of(eventoA, eventoB));

        assertThat(result.eventos()).hasSize(2);
        assertThat(result.mergedGroups()).isEmpty();
    }

    @Test
    void mergesSameEventWithinSevenDayPosteriorWindowButNotAfterIt() throws Exception {
        var eventoA = event("2026-03009", "Festival da Juventude", "2026-07-10T20:00:00Z", null, "Caruaru", "PE", null, null, List.of());
        var eventoB = event("2026-03010", "Festival da Juventude", "2026-07-16T20:00:00Z", null, "Caruaru", "PE", null, null, List.of());
        var eventoC = event("2026-03011", "Festival da Juventude", "2026-07-22T20:00:00Z", null, "Caruaru", "PE", null, null, List.of());

        var result = service(SETTINGS).deduplicate(List.of(eventoB, eventoC, eventoA));

        assertThat(result.eventos()).hasSize(2);
        assertThat(result.mergedGroups()).singleElement().satisfies(group -> {
            assertThat(group.codigoPrincipal()).isEqualTo("2026-03009");
            assertThat(group.codigosMesclados()).contains("2026-03010").doesNotContain("2026-03011");
        });
    }

    @Test
    void respectsConfiguredBlockingStrategies() throws Exception {
        var settings = new DeduplicationSettings(SETTINGS.inputPath(), true, false, 0.82d, 0.62d, List.of("hash"), List.of(), "", SETTINGS.openRouterModel(), SETTINGS.openRouterBaseUrl(), "", SETTINGS.geminiModel(), SETTINGS.geminiBaseUrl(), SETTINGS.ollamaModel(), SETTINGS.ollamaBaseUrl(), "", "", "", "");
        var eventoA = event("2026-04030", "Festival do Rio", "2026-05-10T20:00:00Z", "Praca Central", "Rio Branco", "AC", null, null, List.of());
        var eventoB = event("2026-04031", "Festival do Rio", "2026-05-10T20:00:00Z", "Praca Central", "Rio Branco", "AC", null, null, List.of());

        var result = service(settings).deduplicate(List.of(eventoA, eventoB));

        assertThat(result.eventos()).hasSize(2);
        assertThat(result.mergedGroups()).isEmpty();
    }

    @Test
    void usesAiAsTieBreakerInGrayZone() throws Exception {
        var settings = new DeduplicationSettings(SETTINGS.inputPath(), true, true, 0.98d, 0.62d, SETTINGS.blockingStrategies(), List.of(), "", SETTINGS.openRouterModel(), SETTINGS.openRouterBaseUrl(), "", SETTINGS.geminiModel(), SETTINGS.geminiBaseUrl(), SETTINGS.ollamaModel(), SETTINGS.ollamaBaseUrl(), "", "", "", "");
        var eventoA = event("2026-04010", "Festival da Juventude", "2026-08-21T20:00:00Z", "Praca Central", "Caruaru", "PE", null, null, List.of("Banda Aurora"));
        var eventoB = event("2026-04011", "Festival da Juventude 2026", "2026-08-21T20:00:00Z", "Praca Central", "Caruaru", "PE", null, null, List.of("Banda Aurora"));
        var ai = new StubAiDuplicateDecider(new AiDuplicateDecision(true, 0.91d, "mesmo evento", ProviderIA.OPEN_ROUTER));

        var result = new EventoDeduplicationService(ai, settings).deduplicate(List.of(eventoA, eventoB));

        assertThat(result.eventos()).hasSize(1);
        assertThat(result.aiDecisionTraces()).singleElement().satisfies(trace -> {
            assertThat(trace.provider()).isEqualTo(ProviderIA.OPEN_ROUTER);
            assertThat(trace.justificativa()).isEqualTo("mesmo evento");
        });
        assertThat(result.stats().aiPairsEvaluated()).isEqualTo(1);
        assertThat(result.stats().aiPairsAccepted()).isEqualTo(1);
        assertThat(ai.callCount).isEqualTo(1);
    }

    @Test
    void doesNotActivateOllamaOnlyWithDefaultFallback() {
        var settings = new DeduplicationSettings(SETTINGS.inputPath(), true, true, 0.82d, 0.62d, List.of("title_city_date"), List.of("ollama"), "", SETTINGS.openRouterModel(), SETTINGS.openRouterBaseUrl(), "", SETTINGS.geminiModel(), SETTINGS.geminiBaseUrl(), "llama3:8b", "http://localhost:11434", "", "", "", "");

        assertThat(settings.availableProviders()).isEmpty();
    }

    private static EventoDeduplicationService service(DeduplicationSettings settings) {
        return new EventoDeduplicationService(NullAiDuplicateDecider.INSTANCE, settings);
    }

    /**
     * Builds an {@link Evento} using the record's canonical constructor.
     * <p>
     * Only the fields exercised by the deduplication tests are populated with meaningful
     * values; every other field falls back to a deterministic default so the call site
     * stays concise and the test's intent is obvious.
     */
    private static Evento event(String codigo, String titulo, String dataInicio, String local, String municipio, String uf, String dataTermino, String promotorNome, List<String> interpretes) {
        return new Evento(
            null,
            codigo,
            titulo,
            dataInicio == null ? null : OffsetDateTime.parse(dataInicio),
            dataTermino == null ? null : OffsetDateTime.parse(dataTermino),
            local,
            municipio,
            uf,
            null,
            null,
            null,
            promotorNome,
            null,
            interpretes == null ? new ArrayList<>() : new ArrayList<>(interpretes),
            null,
            null,
            null,
            null,
            StatusEvento.AGENDADO,
            StatusSGA.NAO_VERIFICADO,
            NivelCompletude.INSUFICIENTE,
            TipoCanal.AGREGADOR_GOV,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            null,
            null,
            new ArrayList<>()
        );
    }

    private static Evidencia evidence(TipoEvidencia type, String url, String hash) {
        return new Evidencia(
            1,
            type,
            url,
            "",
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            hash,
            null,
            null,
            null
        );
    }

    private static final class StubAiDuplicateDecider implements AiDuplicateDecider {
        private final AiDuplicateDecision decision;
        private int callCount;

        private StubAiDuplicateDecider(AiDuplicateDecision decision) {
            this.decision = decision;
        }

        @Override
        public boolean isEnabled() {
            return decision != null;
        }

        @Override
        public AiDuplicateDecision decide(Evento left, Evento right, double heuristicScore) {
            callCount++;
            return decision;
        }
    }
}
