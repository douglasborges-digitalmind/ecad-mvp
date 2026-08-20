package br.com.ecad.captacao.shared.infrastructure.mongodb;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;

import br.com.ecad.captacao.shared.TextNormalization;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.MunicipioUnidadeRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoEventoRepository extends MongoRepositoryBase<Evento> implements EventoRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoEventoRepository.class);

    private final MunicipioUnidadeRepository municipioUnidadeRepository;

    public MongoEventoRepository(MongoClient client, String databaseName) {
        this(client, databaseName, null);
    }

    public MongoEventoRepository(MongoClient client, String databaseName,
                                  MunicipioUnidadeRepository municipioUnidadeRepository) {
        super(client, databaseName, MongoCollectionNames.EVENTOS, Evento.class);
        this.municipioUnidadeRepository = municipioUnidadeRepository;
    }

    @Override
    protected String getPartitionKeyValue(Evento item) {
        return item.municipio() == null ? "" : item.municipio();
    }

    @Override
    protected String getId(Evento item) {
        return item.id().toString();
    }

    @Override
    public Evento criar(Evento evento) {
        ensureIndexes();
        return super.criarItem(evento);
    }

    @Override
    public Optional<Evento> obterPorId(UUID id, String municipio) {
        return super.obterPorId(id.toString(), municipio);
    }

    @Override
    public Optional<Evento> buscarPorDedup(String titulo, String local, OffsetDateTime data, String municipio, String uf) {
        var dataMin = data.toLocalDate().atStartOfDay().atOffset(data.getOffset());
        var dataMax = dataMin.plusDays(1);

        // Filtro no banco para reduzir resultados, depois comparação textual normalizada em memória
        var filter = Filters.and(
            Filters.eq("municipio", municipio == null ? "" : municipio),
            Filters.eq("uf", uf),
            Filters.gte("data_inicio", dataMin),
            Filters.lt("data_inicio", dataMax));

        return executarQuery(filter, municipio, true).stream()
            .filter(item -> TextNormalization.equalsForComparison(item.titulo(), titulo))
            .filter(item -> TextNormalization.equalsForComparison(item.local(), local))
            .filter(item -> TextNormalization.equalsForComparison(item.municipio(), municipio))
            .filter(item -> TextNormalization.equalsForComparison(item.uf(), uf))
            .findFirst();
    }

    @Override
    public Evento atualizar(Evento evento) {
        return super.atualizarItem(evento);
    }

    @Override
    public List<Evento> listar(String municipio, StatusEvento status, StatusSGA statusSga, NivelCompletude nivelCompletude,
        OffsetDateTime dataInicio, OffsetDateTime dataTermino, String codigoEvento, String unidadeEcad) {
        var clauses = new ArrayList<Bson>();
        if (municipio != null && !municipio.isBlank()) clauses.add(Filters.eq("municipio", municipio));
        if (status != null) clauses.add(Filters.eq("status", status.jsonValue()));
        if (statusSga != null) clauses.add(Filters.eq("status_sga", statusSga.jsonValue()));
        if (nivelCompletude != null) clauses.add(Filters.eq("nivel_completude", nivelCompletude.jsonValue()));
        if (dataInicio != null) clauses.add(Filters.gte("data_inicio", dataInicio));
        if (dataTermino != null) clauses.add(Filters.lte("data_inicio", dataTermino));
        if (codigoEvento != null && !codigoEvento.isBlank()) clauses.add(Filters.eq("codigo_evento", codigoEvento));
        if (unidadeEcad != null && !unidadeEcad.isBlank()) clauses.add(Filters.eq("unidade_ecad", unidadeEcad));

        var filter = clauses.isEmpty() ? Filters.empty() : Filters.and(clauses);
        return executarQuery(filter, municipio, true);
    }

    @Override
    public List<Evento> listarParaPlanilha() {
        if (municipioUnidadeRepository == null) {
            return listarParaPlanilhaFullScan();
        }
        var aggregated = new ArrayList<Evento>();
        try {
            for (var mu : municipioUnidadeRepository.listarMunicipios()) {
                if (mu.municipio == null || mu.municipio.isBlank()) continue;
                var filter = Filters.and(
                    Filters.eq("municipio", mu.municipio),
                    Filters.ne("nivel_completude", NivelCompletude.INSUFICIENTE.jsonValue()));
                aggregated.addAll(executarQuery(filter, mu.municipio, true));
            }
        } catch (Exception ex) {
            LOGGER.warn("Falha ao listar municipios; usando fallback full-scan.", ex);
            return listarParaPlanilhaFullScan();
        }
        return ordenarParaPlanilha(aggregated);
    }

    private List<Evento> listarParaPlanilhaFullScan() {
        var filter = Filters.ne("nivel_completude", NivelCompletude.INSUFICIENTE.jsonValue());
        return ordenarParaPlanilha(executarQuery(filter, null, true));
    }

    private static List<Evento> ordenarParaPlanilha(List<Evento> eventos) {
        return eventos.stream()
            .sorted(java.util.Comparator
                .comparing((Evento e) -> nullToEmpty(e.unidadeEcad()))
                .thenComparingInt(e -> ordemSga(e.statusSga()))
                .thenComparing(e -> e.dataInicio(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
            .toList();
    }

    private static int ordemSga(StatusSGA status) {
        if (status == StatusSGA.INEDITO) return 0;
        if (status == StatusSGA.NAO_VERIFICADO) return 1;
        return 2;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public List<Evento> listarPorStatusSga(StatusSGA statusSga) {
        if (municipioUnidadeRepository == null) return listarPorStatusSgaFullScan(statusSga);
        var aggregated = new ArrayList<Evento>();
        try {
            for (var mu : municipioUnidadeRepository.listarMunicipios()) {
                if (mu.municipio == null || mu.municipio.isBlank()) continue;
                var filter = Filters.and(
                    Filters.eq("municipio", mu.municipio),
                    Filters.eq("status_sga", statusSga.jsonValue()));
                aggregated.addAll(executarQuery(filter, mu.municipio, true));
            }
        } catch (Exception ex) {
            LOGGER.warn("Falha ao listar municipios em listarPorStatusSga; fallback full-scan.", ex);
            return listarPorStatusSgaFullScan(statusSga);
        }
        return aggregated;
    }

    private List<Evento> listarPorStatusSgaFullScan(StatusSGA statusSga) {
        return executarQuery(Filters.eq("status_sga", statusSga.jsonValue()), null, true);
    }

    private void ensureIndexes() {
        try {
            collection.createIndex(new Document("municipio", 1));
            collection.createIndex(new Document("status_sga", 1));
            collection.createIndex(new Document("data_inicio", -1));
            collection.createIndex(new Document("titulo_normalizado", 1));
            collection.createIndex(new Document("unidade_ecad", 1));
        } catch (Exception ex) {
            LOGGER.warn("Falha ao criar índices MongoDB (podem já existir): {}", ex.getMessage());
        }
    }
}