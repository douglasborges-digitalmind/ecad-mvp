package br.com.ecad.captacao.controlcenter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.controlcenter.services.EventPublisher;
import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eventos")
class EventosController {
    private final EventoRepository repository;
    private final EventPublisher publisher;

    EventosController(EventoRepository repository, EventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @GetMapping
    List<Evento> listar(
        @RequestParam(name = "municipio", required = false) String municipio,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "status_sga", required = false) String statusSga,
        @RequestParam(name = "nivel_completude", required = false) String nivelCompletude,
        @RequestParam(name = "data_inicio", required = false) String dataInicio,
        @RequestParam(name = "data_termino", required = false) String dataTermino,
        @RequestParam(name = "codigo_evento", required = false) String codigoEvento,
        @RequestParam(name = "unidade_ecad", required = false) String unidadeEcad) throws Exception {
        return repository.listar(
            municipio,
            parseStatus(status),
            parseStatusSga(statusSga),
            parseNivel(nivelCompletude),
            parseDate(dataInicio),
            parseDate(dataTermino),
            codigoEvento,
            unidadeEcad);
    }

    @GetMapping("/{id}")
    ResponseEntity<Evento> obter(
        @PathVariable("id") UUID id,
        @RequestParam(name = "municipio", required = false) String municipio) throws Exception {
        if (municipio == null || municipio.isBlank()) {
            throw new IllegalArgumentException("Parametro municipio e obrigatorio para buscar evento por ID (partition key).");
        }
        var evento = repository.obterPorId(id, municipio);
        return evento.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/captured")
    ResponseEntity<Void> publicarDocumentoCapturado(@RequestBody DocumentoCapturado documento) throws Exception {
        if (documento == null
            || isBlank(documento.urlOrigem())
            || isBlank(documento.urlStagingInterno())
            || documento.idFonteCaptacao() == null
            || documento.idFonteCaptacao().equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("DocumentoCapturado invalido. Campos obrigatorios: url_origem, url_staging_interno, id_fonte_captacao.");
        }

        publisher.publicarDocumentoCapturado(documento);
        return ResponseEntity.accepted().build();
    }

    private static StatusEvento parseStatus(String value) {
        return isBlank(value) ? null : StatusEvento.fromJson(value);
    }

    private static StatusSGA parseStatusSga(String value) {
        return isBlank(value) ? null : StatusSGA.fromJson(value);
    }

    private static NivelCompletude parseNivel(String value) {
        return isBlank(value) ? null : NivelCompletude.fromJson(value);
    }

    private static OffsetDateTime parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }

        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
        }

        return OffsetDateTime.parse(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}