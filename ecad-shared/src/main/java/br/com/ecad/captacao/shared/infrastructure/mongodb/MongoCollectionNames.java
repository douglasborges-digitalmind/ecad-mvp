package br.com.ecad.captacao.shared.infrastructure.mongodb;

/** Nomes das colecoes MongoDB, equivalentes aos containers Cosmos DB. */
public final class MongoCollectionNames {
    public static final String EVENTOS = "eventos";
    public static final String DOCUMENTOS = "documentos";
    public static final String FONTES_CAPTACAO = "fontes_captacao";
    public static final String DESTINATARIOS = "destinatarios";
    public static final String CRITERIOS_EXTRACAO = "criterios_extracao";
    public static final String METRICAS_IA = "metricas_ia";
    public static final String METRICAS_OPERACIONAIS = "metricas_operacionais";
    public static final String MUNICIPIOS_UNIDADE = "municipios_unidade";
    public static final String SEQUENCIAIS = "sequenciais";

    private MongoCollectionNames() {}
}