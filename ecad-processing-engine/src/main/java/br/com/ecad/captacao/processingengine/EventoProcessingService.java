package br.com.ecad.captacao.processingengine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.contracts.DocumentoCapturado;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.entities.Evidencia;
import br.com.ecad.captacao.shared.domain.entities.MunicipioUnidade;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MunicipioUnidadeRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.SequencialRepository;
import br.com.ecad.captacao.shared.referencedata.MunicipioUnidadeReferenceCatalog;
import org.springframework.stereotype.Service;

@Service
class EventoProcessingService {
    private final EventoRepository eventos;
    private final SequencialRepository sequenciais;
    private final MunicipioUnidadeRepository municipios;

    EventoProcessingService(EventoRepository eventos, SequencialRepository sequenciais, MunicipioUnidadeRepository municipios) {
        this.eventos = eventos;
        this.sequenciais = sequenciais;
        this.municipios = municipios;
    }

    Evento processar(DocumentoCapturado documento, ExtractionResult resultado, String urlArmazenamento, String linkFonte, StatusSGA statusSga) throws Exception {
        ExtractionFieldNormalizer.normalize(resultado);
        applyOfficialLocationNormalization(resultado);
        var existente = findExisting(documento, resultado, linkFonte);
        return existente == null
            ? criar(documento, resultado, urlArmazenamento, linkFonte, statusSga)
            : enriquecer(existente, documento, resultado, urlArmazenamento, linkFonte, statusSga);
    }

    private Evento findExisting(DocumentoCapturado documento, ExtractionResult resultado, String linkFonte) throws Exception {
        if (isBlank(resultado.titulo) || resultado.dataInicio == null || isBlank(resultado.municipio) || isBlank(resultado.uf)) {
            return null;
        }
        var dataInicioOffset = resultado.dataInicio.atStartOfDay().atOffset(ZoneOffset.UTC);
        if (!isBlank(resultado.local)) {
            var exact = eventos.buscarPorDedup(resultado.titulo, resultado.local, dataInicioOffset, resultado.municipio, resultado.uf);
            if (exact.isPresent()) {
                return exact.get();
            }
        }
        var candidates = eventos.listar(resultado.municipio, null, null, null, dataInicioOffset, dataInicioOffset, null, null);
        var evidence = createEvidence(documento, "", linkFonte);
        return candidates.stream()
            .filter(candidate -> candidate.dataInicio() != null && candidate.dataInicio().toLocalDate().equals(resultado.dataInicio))
            .filter(candidate -> TextNormalization.equalsForComparison(candidate.municipio(), resultado.municipio))
            .filter(candidate -> TextNormalization.equalsForComparison(candidate.uf(), resultado.uf))
            .filter(candidate -> hasDuplicateEvidence(candidate, evidence)
                || TextNormalization.equalsForComparison(candidate.titulo(), resultado.titulo) && locationsCompatible(candidate.local(), resultado.local))
            .findFirst()
            .orElse(null);
    }

    private Evento criar(DocumentoCapturado documento, ExtractionResult resultado, String urlArmazenamento, String linkFonte, StatusSGA statusSga) throws Exception {
        var ano = OffsetDateTime.now(ZoneOffset.UTC).getYear();
        var dataInicioOffset = resultado.dataInicio != null ? resultado.dataInicio.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        var dataTerminoOffset = resultado.dataTermino != null ? resultado.dataTermino.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        var evento = new Evento(
            null,
            Evento.gerarCodigoEvento(ano, sequenciais.proximoSequencial(ano)),
            resultado.titulo,
            dataInicioOffset,
            dataTerminoOffset,
            resultado.local,
            resultado.municipio,
            resultado.uf,
            resolveUnidade(resultado.municipio, resultado.uf),
            resultado.hora,
            resultado.promotorCnpj,
            resultado.promotorNome,
            resultado.promotorContato,
            resultado.interpretes,
            resultado.tipoMusica,
            resultado.cobrancaIngresso,
            resultado.valorIngresso == null ? null : resultado.valorIngresso.doubleValue(),
            resultado.capacidadePublico,
            br.com.ecad.captacao.shared.domain.enums.StatusEvento.AGENDADO,
            statusSga,
            br.com.ecad.captacao.shared.domain.enums.NivelCompletude.INSUFICIENTE,
            resolveFontePrimaria(documento.tipo()),
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            resultado.observacoesIa,
            documento.idFonteCaptacao(),
            new java.util.ArrayList<>()
        );
        evento = ExtractionFieldNormalizer.normalize(evento);
        evento = addEvidence(evento, documento, urlArmazenamento, linkFonte);
        evento = evento.comStatusAtualizado(OffsetDateTime.now(ZoneOffset.UTC));
        evento = evento.comNivelCompletudeAtualizado();
        return eventos.criar(evento);
    }

    private Evento enriquecer(Evento evento, DocumentoCapturado documento, ExtractionResult resultado, String urlArmazenamento, String linkFonte, StatusSGA statusSga) throws Exception {
        evento = ExtractionFieldNormalizer.normalize(evento);
        var dataInicioOffset = resultado.dataInicio != null ? resultado.dataInicio.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        var dataTerminoOffset = resultado.dataTermino != null ? resultado.dataTermino.atStartOfDay().atOffset(ZoneOffset.UTC) : null;
        var incoming = new Evento(
            null, null, resultado.titulo, dataInicioOffset, dataTerminoOffset,
            resultado.local, resultado.municipio, resultado.uf, null, resultado.hora,
            resultado.promotorCnpj, resultado.promotorNome, resultado.promotorContato,
            resultado.interpretes, resultado.tipoMusica, resultado.cobrancaIngresso,
            resultado.valorIngresso == null ? null : resultado.valorIngresso.doubleValue(),
            resultado.capacidadePublico, null, null, null, null, null, null, null, null, null
        );
        evento = evento.enriquecidoCom(incoming);
        evento = ExtractionFieldNormalizer.normalize(evento);
        evento = applyOfficialLocationNormalization(evento);
        if (isBlank(evento.unidadeEcad())) {
            evento = evento.comUnidadeEcad(resolveUnidade(evento.municipio(), evento.uf()));
        }
        evento = evento.comStatusSga(statusSga);
        evento = addEvidence(evento, documento, urlArmazenamento, linkFonte);
        evento = evento.comStatusAtualizado(OffsetDateTime.now(ZoneOffset.UTC));
        evento = evento.comNivelCompletudeAtualizado();
        return eventos.atualizar(evento);
    }

    private String resolveUnidade(String municipio, String uf) throws Exception {
        if (isBlank(municipio) || isBlank(uf)) {
            return null;
        }
        var repository = municipios.buscarPorUfMunicipio(uf, municipio).map(item -> item.unidadeEcad);
        if (repository.isPresent()) {
            return repository.get();
        }
        return MunicipioUnidadeReferenceCatalog.tryResolve(municipio, uf).map(item -> item.unidadeEcad).orElse(null);
    }

    private void applyOfficialLocationNormalization(ExtractionResult resultado) throws Exception {
        var official = resolveOfficial(resultado.municipio, resultado.uf);
        if (official != null) {
            ExtractionFieldNormalizer.applyOfficialMunicipality(resultado, official.municipio, official.uf);
        }
    }

    private Evento applyOfficialLocationNormalization(Evento evento) throws Exception {
        var official = resolveOfficial(evento.municipio(), evento.uf());
        if (official != null) {
            return ExtractionFieldNormalizer.applyOfficialMunicipality(evento, official.municipio, official.uf);
        }
        return evento;
    }

    private MunicipioUnidade resolveOfficial(String municipio, String uf) throws Exception {
        var catalog = MunicipioUnidadeReferenceCatalog.tryResolve(municipio, uf);
        if (catalog.isPresent()) {
            return catalog.get();
        }
        if (isBlank(municipio) || isBlank(uf)) {
            return null;
        }
        return municipios.buscarPorUfMunicipio(uf, municipio).orElse(null);
    }

    private static TipoCanal resolveFontePrimaria(TipoEvidencia tipo) {
        return switch (tipo) {
            case CONTRATO_MUSICAL -> TipoCanal.AGREGADOR_GOV;
        };
    }

    private static Evento addEvidence(Evento evento, DocumentoCapturado documento, String urlArmazenamento, String linkFonte) {
        var evidence = createEvidence(documento, urlArmazenamento, linkFonte);
        if (hasDuplicateEvidence(evento, evidence)) {
            return evento;
        }
        return evento.comEvidenciaAdicionada(evidence);
    }

    private static Evidencia createEvidence(DocumentoCapturado documento, String urlArmazenamento, String linkFonte) {
        return new Evidencia(
            0, // sequencia será calculada e sobrescrita por comEvidenciaAdicionada
            documento.tipo(),
            isBlank(linkFonte) ? documento.urlOrigem() : linkFonte,
            urlArmazenamento,
            documento.timestamp(),
            documento.hashConteudo(),
            documento.metadados() != null ? documento.metadados().get("json_bruto_url_interna") : null,
            documento.metadados() != null ? documento.metadados().get("evidencia_visual_url_interna") : null,
            null
        );
    }

    private static boolean hasDuplicateEvidence(Evento evento, Evidencia evidence) {
        return evento.evidencias() != null && evento.evidencias().stream().anyMatch(existing ->
            TextNormalization.equalsForComparison(existing.hashArquivo(), evidence.hashArquivo())
                || TextNormalization.equalsForComparison(existing.urlOrigem(), evidence.urlOrigem()));
    }

    private static boolean locationsCompatible(String left, String right) {
        if (TextNormalization.equalsForComparison(left, right)) {
            return true;
        }
        if (isBlank(left) || isBlank(right)) {
            return true;
        }
        return tokenSimilarity(left, right) >= 0.65d;
    }

    private static double tokenSimilarity(String left, String right) {
        var leftTokens = tokens(left);
        var rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0d;
        }
        var intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        var union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0d : (double) intersection.size() / union.size();
    }

    private static HashSet<String> tokens(String value) {
        var result = new HashSet<String>();
        for (var token : TextNormalization.normalizeForComparison(value).split("\\s+")) {
            if (token.length() > 1) {
                result.add(token);
            }
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
