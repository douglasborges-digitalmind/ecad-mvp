package br.com.ecad.captacao.shared.domain.entities;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.domain.enums.TipoMusica;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Evento(
    @JsonProperty("id") UUID id,
    @JsonProperty("codigo_evento") String codigoEvento,
    @JsonProperty("titulo") String titulo,
    @JsonProperty("data_inicio") OffsetDateTime dataInicio,
    @JsonProperty("data_termino") OffsetDateTime dataTermino,
    @JsonProperty("local") String local,
    @JsonProperty("municipio") String municipio,
    @JsonProperty("uf") String uf,
    @JsonProperty("unidade_ecad") String unidadeEcad,
    @JsonProperty("hora") String hora,
    @JsonProperty("promotor_cnpj") String promotorCnpj,
    @JsonProperty("promotor_nome") String promotorNome,
    @JsonProperty("promotor_contato") String promotorContato,
    @JsonProperty("interpretes") List<String> interpretes,
    @JsonProperty("tipo_musica") TipoMusica tipoMusica,
    @JsonProperty("cobranca_ingresso") CobrancaIngresso cobrancaIngresso,
    @JsonProperty("valor_ingresso") Double valorIngresso,
    @JsonProperty("capacidade_publico") Integer capacidadePublico,
    @JsonProperty("status") StatusEvento status,
    @JsonProperty("status_sga") StatusSGA statusSga,
    @JsonProperty("nivel_completude") NivelCompletude nivelCompletude,
    @JsonProperty("fonte_primaria") TipoCanal fontePrimaria,
    @JsonProperty("data_descoberta") OffsetDateTime dataDescoberta,
    @JsonProperty("data_atualizacao") OffsetDateTime dataAtualizacao,
    @JsonProperty("observacoes_ia") String observacoesIa,
    @JsonProperty("id_fonte_captacao") UUID idFonteCaptacao,
    @JsonProperty("evidencias") List<Evidencia> evidencias
) {
    public Evento {
        if (id == null) id = UUID.randomUUID();
        if (codigoEvento == null) codigoEvento = "";
        if (interpretes == null) interpretes = new ArrayList<>();
        if (evidencias == null) evidencias = new ArrayList<>();
        if (status == null) status = StatusEvento.AGENDADO;
        if (statusSga == null) statusSga = StatusSGA.NAO_VERIFICADO;
        if (nivelCompletude == null) nivelCompletude = NivelCompletude.INSUFICIENTE;
        if (dataDescoberta == null) dataDescoberta = OffsetDateTime.now();
        if (dataAtualizacao == null) dataAtualizacao = OffsetDateTime.now();
    }

    public StatusEvento calcularStatus(OffsetDateTime dataReferencia) {
        if (status == StatusEvento.CANCELADO) {
            return status;
        }

        var agora = dataReferencia == null ? OffsetDateTime.now() : dataReferencia;
        var inicio = dataInicio == null ? dataTermino : dataInicio;
        var fim = dataTermino == null ? dataInicio : dataTermino;

        if (inicio == null || fim == null) {
            return status;
        }

        if (agora.toLocalDate().isBefore(inicio.toLocalDate())) {
            return StatusEvento.AGENDADO;
        }

        if (!agora.toLocalDate().isAfter(fim.toLocalDate())) {
            return StatusEvento.EM_ANDAMENTO;
        }

        return StatusEvento.REALIZADO;
    }

    public NivelCompletude calcularNivelCompletude() {
        var camposMinimos = hasText(titulo)
            && dataInicio != null
            && hasText(local)
            && hasText(municipio)
            && hasText(uf)
            && hasText(unidadeEcad);

        if (!camposMinimos) {
            return NivelCompletude.INSUFICIENTE;
        }

        var temInterpretePrincipal = interpretes != null && !interpretes.isEmpty();
        var totalEvidencias = evidencias == null ? 0 : evidencias.size();

        if (temInterpretePrincipal && totalEvidencias >= 3) {
            return NivelCompletude.ALTO;
        }

        if (totalEvidencias >= 2) {
            return NivelCompletude.MEDIO;
        }

        return NivelCompletude.BASICO;
    }

    public boolean ehDuplicataDe(Evento outro) {
        return TextNormalization.equalsForComparison(titulo, outro.titulo)
            && TextNormalization.equalsForComparison(local, outro.local)
            && sameDate(dataInicio, outro.dataInicio)
            && TextNormalization.equalsForComparison(municipio, outro.municipio)
            && TextNormalization.equalsForComparison(uf, outro.uf);
    }

    public static String gerarCodigoEvento(int ano, int sequencial) {
        return "%04d-%05d".formatted(ano, sequencial);
    }

    public Evento enriquecidoCom(Evento outro) {
        var novosInterpretes = new ArrayList<>(this.interpretes);
        if (outro.interpretes != null) {
            for (var interprete : outro.interpretes) {
                if (hasText(interprete) && !novosInterpretes.contains(interprete)) {
                    novosInterpretes.add(interprete);
                }
            }
        }

        return new Evento(
            this.id,
            this.codigoEvento,
            firstNonNull(this.titulo, outro.titulo),
            firstNonNull(this.dataInicio, outro.dataInicio),
            firstNonNull(this.dataTermino, outro.dataTermino),
            firstNonNull(this.local, outro.local),
            firstNonNull(this.municipio, outro.municipio),
            firstNonNull(this.uf, outro.uf),
            firstNonNull(this.unidadeEcad, outro.unidadeEcad),
            firstNonNull(this.hora, outro.hora),
            firstNonNull(this.promotorCnpj, outro.promotorCnpj),
            firstNonNull(this.promotorNome, outro.promotorNome),
            firstNonNull(this.promotorContato, outro.promotorContato),
            novosInterpretes,
            firstNonNull(this.tipoMusica, outro.tipoMusica),
            firstNonNull(this.cobrancaIngresso, outro.cobrancaIngresso),
            firstNonNull(this.valorIngresso, outro.valorIngresso),
            firstNonNull(this.capacidadePublico, outro.capacidadePublico),
            this.status,
            this.statusSga,
            this.nivelCompletude,
            firstNonNull(this.fontePrimaria, outro.fontePrimaria),
            this.dataDescoberta,
            OffsetDateTime.now(),
            hasText(outro.observacoesIa) && !hasText(this.observacoesIa) ? outro.observacoesIa : this.observacoesIa,
            this.idFonteCaptacao,
            this.evidencias
        );
    }

    public Evento comStatusAtualizado(OffsetDateTime dataReferencia) {
        return new Evento(
            this.id, this.codigoEvento, this.titulo, this.dataInicio, this.dataTermino,
            this.local, this.municipio, this.uf, this.unidadeEcad, this.hora,
            this.promotorCnpj, this.promotorNome, this.promotorContato, this.interpretes,
            this.tipoMusica, this.cobrancaIngresso, this.valorIngresso, this.capacidadePublico,
            calcularStatus(dataReferencia), this.statusSga, this.nivelCompletude, this.fontePrimaria,
            this.dataDescoberta, OffsetDateTime.now(), this.observacoesIa, this.idFonteCaptacao, this.evidencias
        );
    }

    public Evento comNivelCompletudeAtualizado() {
        return new Evento(
            this.id, this.codigoEvento, this.titulo, this.dataInicio, this.dataTermino,
            this.local, this.municipio, this.uf, this.unidadeEcad, this.hora,
            this.promotorCnpj, this.promotorNome, this.promotorContato, this.interpretes,
            this.tipoMusica, this.cobrancaIngresso, this.valorIngresso, this.capacidadePublico,
            this.status, this.statusSga, calcularNivelCompletude(), this.fontePrimaria,
            this.dataDescoberta, OffsetDateTime.now(), this.observacoesIa, this.idFonteCaptacao, this.evidencias
        );
    }

    public Evento comEvidenciaAdicionada(Evidencia evidencia) {
        var novasEvidencias = new ArrayList<>(this.evidencias);
        int nextSequencia = 1;
        if (!novasEvidencias.isEmpty()) {
            nextSequencia = novasEvidencias.stream().mapToInt(Evidencia::sequencia).max().orElse(0) + 1;
        }
        var evidenciaComSequencia = new Evidencia(
            nextSequencia,
            evidencia.tipo(),
            evidencia.urlOrigem(),
            evidencia.urlArmazenamentoInterno(),
            evidencia.dataCaptura(),
            evidencia.hashArquivo(),
            evidencia.jsonBrutoUrlInterna(),
            evidencia.evidenciaVisualUrlInterna(),
            evidencia.observacoesIa()
        );
        novasEvidencias.add(evidenciaComSequencia);
        return new Evento(
            this.id, this.codigoEvento, this.titulo, this.dataInicio, this.dataTermino,
            this.local, this.municipio, this.uf, this.unidadeEcad, this.hora,
            this.promotorCnpj, this.promotorNome, this.promotorContato, this.interpretes,
            this.tipoMusica, this.cobrancaIngresso, this.valorIngresso, this.capacidadePublico,
            this.status, this.statusSga, this.nivelCompletude, this.fontePrimaria,
            this.dataDescoberta, OffsetDateTime.now(), this.observacoesIa, this.idFonteCaptacao, novasEvidencias
        );
    }

    public Evento comStatusSga(StatusSGA novoStatusSga) {
        return new Evento(
            this.id, this.codigoEvento, this.titulo, this.dataInicio, this.dataTermino,
            this.local, this.municipio, this.uf, this.unidadeEcad, this.hora,
            this.promotorCnpj, this.promotorNome, this.promotorContato, this.interpretes,
            this.tipoMusica, this.cobrancaIngresso, this.valorIngresso, this.capacidadePublico,
            this.status, novoStatusSga, this.nivelCompletude, this.fontePrimaria,
            this.dataDescoberta, OffsetDateTime.now(), this.observacoesIa, this.idFonteCaptacao, this.evidencias
        );
    }

    public Evento comUnidadeEcad(String novaUnidadeEcad) {
        return new Evento(
            this.id, this.codigoEvento, this.titulo, this.dataInicio, this.dataTermino,
            this.local, this.municipio, this.uf, novaUnidadeEcad, this.hora,
            this.promotorCnpj, this.promotorNome, this.promotorContato, this.interpretes,
            this.tipoMusica, this.cobrancaIngresso, this.valorIngresso, this.capacidadePublico,
            this.status, this.statusSga, this.nivelCompletude, this.fontePrimaria,
            this.dataDescoberta, this.dataAtualizacao, this.observacoesIa, this.idFonteCaptacao, this.evidencias
        );
    }

    private static boolean sameDate(OffsetDateTime left, OffsetDateTime right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.toLocalDate().equals(right.toLocalDate());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> T firstNonNull(T current, T candidate) {
        return current == null ? candidate : current;
    }
}