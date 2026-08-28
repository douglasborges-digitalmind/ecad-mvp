package br.com.ecad.captacao.controlcenter.services;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.repositories.DestinatarioRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.stereotype.Service;

/**
 * Geracao da planilha XLSX final do MVP (HU-01..HU-05, RF-06.1..RF-06.9).
 * Streaming via {@link SXSSFWorkbook} para escalar quantidade de eventos sem materializar
 * a planilha inteira em memoria, e cores/Magic Link/hyperlinks conforme PRD.
 */
@Service
public class PlanilhaService {
    private static final int STREAM_WINDOW_ROWS = 200;
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SHEET_EVENTOS = "Eventos Capturados";
    private static final String SHEET_EVIDENCIAS = "Evidências";
    private static final String SHEET_RESUMO = "Resumo Executivo";

    // Paleta extraída do Template-Planilha-Eventos-ECAD.xlsx (styles.xml).
    private static final byte[] COR_IDENTIFICACAO = hex("1B4F72"); // azul
    private static final byte[] COR_COMPLEMENTAR = hex("117A65"); // verde
    private static final byte[] COR_EVIDENCIA = hex("7B241C");    // vinho
    private static final byte[] COR_INTELIGENCIA = hex("4A235A"); // roxo
    private static final byte[] COR_TABELA_HEADER = hex("2C3E50"); // cinza-azulado
    private static final byte[] COR_BORDA = hex("BDC3C7");
    private static final byte[] COR_HYPERLINK = hex("2E86C1");
    private static final byte[] COR_VERDE_TEXTO = hex("1E8449");
    private static final byte[] COR_VERMELHO_TEXTO = hex("922B21");
    private static final byte[] COR_FILL_INEDITO = hex("D5F5E3");
    private static final byte[] COR_FILL_JA_CADASTRADO = hex("FADBD8");
    private static final byte[] COR_TITULO = hex("1B4F72");
    private static final byte[] COR_KPI = hex("1B4F72");
    private static final byte[] COR_KPI_EVIDENCIAS = hex("7B241C");
    private static final byte[] COR_ROTULO_KPI = hex("566573");
    private static final byte[] COR_GERADO_EM = hex("7F8C8D");

    private static byte[] hex(String rgb) {
        var bytes = new byte[3];
        for (int i = 0; i < 3; i++) {
            bytes[i] = (byte) Integer.parseInt(rgb.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /** Larguras de coluna (em caracteres Excel) da aba Eventos Capturados, conforme template. */
    private static final int[] LARGURAS_EVENTOS = {
        18, 40, 16, 13, 28, 24, 6, 22, 32, 26, 26, 36, 16, 18, 16, 18, 16, 22, 16, 18, 18, 24, 16, 16, 55};
    /** Larguras de coluna (em caracteres Excel) da aba Evidências, conforme template. */
    private static final int[] LARGURAS_EVIDENCIAS = {18, 6, 20, 52, 72, 16};
    private static final int LARGURA_RESUMO = 22;

    private static final List<String> HEADERS_EVENTOS = List.of(
        "ID_Evento", "Titulo_Evento", "Data_Realizacao", "Hora_Inicio", "Local_Evento", "Municipio", "UF",
        "Unidade_ECAD", "Promotor", "Contato_Promotor", "Interprete_Principal", "Interpretes_Outros",
        "Tipo_Musica", "Cobranca_Ingresso", "Valor_Ingresso", "Capacidade_Publico", "Qtd_Evidencias",
        "Ver_Evidencias", "Status_Evento", "Status_SGA", "Nivel_Completude", "Fonte_Primaria",
        "Data_Descoberta", "Data_Atualizacao", "Observacoes_IA");
    private static final int FIM_IDENTIFICACAO = 8;
    private static final int FIM_COMPLEMENTAR = 16;
    private static final int FIM_EVIDENCIA = 18;

    private static final List<String> HEADERS_EVIDENCIAS = List.of(
        "ID_Evento", "Seq", "Tipo_Evidencia", "URL_Fonte", "URL_Blob_Storage", "Data_Captura");

    private final EventoRepository eventoRepository;
    private final DestinatarioRepository destinatarioRepository;
    private final EmailService emailService;

    public PlanilhaService(
        EventoRepository eventoRepository,
        DestinatarioRepository destinatarioRepository,
        EmailService emailService) {
        this.eventoRepository = eventoRepository;
        this.destinatarioRepository = destinatarioRepository;
        this.emailService = emailService;
    }

    public byte[] gerarPlanilha() throws IOException {
        return gerarPlanilha(eventoRepository.listarParaPlanilha());
    }

    public byte[] gerarPlanilha(List<Evento> eventos) throws IOException {
        try (var workbook = new SXSSFWorkbook(STREAM_WINDOW_ROWS); var output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);

            var styles = SectionStyles.build(workbook);
            var eventosOrdenados = ordenarParaPlanilha(eventos);

            // Cria as abas na ordem do template (Eventos, Evidências, Resumo).
            var sheetEventos = workbook.createSheet(SHEET_EVENTOS);
            var sheetEvidencias = workbook.createSheet(SHEET_EVIDENCIAS);
            var sheetResumo = workbook.createSheet(SHEET_RESUMO);

            // Preenche Evidências primeiro para obter os números de linha dos Magic Links.
            var primeiraLinhaPorEvento = preencherAbaEvidencias(sheetEvidencias, eventosOrdenados, styles);
            preencherAbaEventos(sheetEventos, eventosOrdenados, styles, primeiraLinhaPorEvento);
            preencherAbaResumo(sheetResumo, eventosOrdenados, styles);

            workbook.write(output);
            workbook.dispose();
            return output.toByteArray();
        }
    }

    public void enviarPorEmail(byte[] planilha) throws Exception {
        var destinatarios = destinatarioRepository.listar();
        if (destinatarios.isEmpty()) {
            return;
        }

        var emails = destinatarios.stream()
            .map(destinatario -> destinatario.email)
            .filter(email -> email != null && !email.isBlank())
            .toList();
        if (emails.isEmpty()) {
            return;
        }

        var data = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
        emailService.enviarPlanilha(
            new ByteArrayInputStream(planilha),
            "Planilha-Eventos-ECAD-" + data + ".xlsx",
            "ECAD Captacao - Planilha de Eventos (" + data + ")",
            "<p>Planilha de eventos ECAD em anexo.</p>",
            emails);
    }

    /** RF-06.5: Unidade_ECAD asc, Status_SGA (INEDITO primeiro), Data_Inicio asc. */
    private static List<Evento> ordenarParaPlanilha(List<Evento> eventos) {
        return eventos.stream()
            .sorted(Comparator
                .comparing((Evento evento) -> nullToEmpty(evento.unidadeEcad()))
                .thenComparingInt(evento -> ordemSga(evento.statusSga()))
                .thenComparing(evento -> evento.dataInicio(), Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static int ordemSga(StatusSGA status) {
        if (status == StatusSGA.INEDITO) {
            return 0;
        }
        if (status == StatusSGA.NAO_VERIFICADO || status == null) {
            return 1;
        }
        return 2;
    }

    private Map<String, Integer> preencherAbaEvidencias(SXSSFSheet sheet, List<Evento> eventos, SectionStyles styles) {
        criarHeader(sheet, HEADERS_EVIDENCIAS, styles.headerEvidencia, LARGURAS_EVIDENCIAS);

        var primeiraLinha = new LinkedHashMap<String, Integer>();
        var creationHelper = sheet.getWorkbook().getCreationHelper();
        var rowIndex = 1;

        for (var evento : eventos) {
            if (evento.evidencias() == null || evento.evidencias().isEmpty()) {
                continue;
            }

            primeiraLinha.put(nullToEmpty(evento.codigoEvento()), rowIndex + 1);
            for (var evidencia : evento.evidencias()) {
                var row = sheet.createRow(rowIndex++);
                write(row, 0, evento.codigoEvento(), styles.celula);
                write(row, 1, evidencia.sequencia(), styles.celula);
                write(row, 2, rotuloTipoEvidencia(evidencia.tipo()), styles.celula);
                writeHyperlink(row, 3, evidencia.urlOrigem(), creationHelper, styles.hyperlink);
                writeHyperlink(row, 4, evidencia.urlArmazenamentoInterno(), creationHelper, styles.hyperlink);
                write(row, 5, format(evidencia.dataCaptura()), styles.celula);
            }
        }

        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, HEADERS_EVIDENCIAS.size() - 1));
        sheet.createFreezePane(0, 1);
        return primeiraLinha;
    }

    private void preencherAbaEventos(
        SXSSFSheet sheet,
        List<Evento> eventos,
        SectionStyles styles,
        Map<String, Integer> primeiraLinhaEvidencia) {
        criarHeaderSecionado(sheet, styles);

        var creationHelper = sheet.getWorkbook().getCreationHelper();
        var rowIndex = 1;
        for (var evento : eventos) {
            var row = sheet.createRow(rowIndex++);
            write(row, 0, evento.codigoEvento(), styles.celula);
            write(row, 1, evento.titulo(), styles.celula);
            write(row, 2, format(evento.dataInicio()), styles.celula);
            write(row, 3, evento.hora(), styles.celula);
            write(row, 4, evento.local(), styles.celula);
            write(row, 5, evento.municipio(), styles.celula);
            write(row, 6, evento.uf(), styles.celula);
            write(row, 7, evento.unidadeEcad(), styles.celula);
            write(row, 8, evento.promotorNome(), styles.celula);
            write(row, 9, evento.promotorContato(), styles.celula);
            write(row, 10, interpretePrincipal(evento.interpretes()), styles.celula);
            write(row, 11, interpretesOutros(evento.interpretes()), styles.celula);
            write(row, 12, rotuloTipoMusica(evento.tipoMusica()), styles.celula);
            write(row, 13, rotuloCobranca(evento.cobrancaIngresso()), styles.celula);
            write(row, 14, formatValorIngresso(evento.valorIngresso(), evento.cobrancaIngresso()), styles.celula);
            write(row, 15, evento.capacidadePublico() == null ? "" : evento.capacidadePublico().toString(), styles.celula);
            write(row, 16, evento.evidencias() == null ? 0 : evento.evidencias().size(), styles.celula);

            var primeira = primeiraLinhaEvidencia.get(nullToEmpty(evento.codigoEvento()));
            if (primeira != null) {
                writeMagicLink(row, 17, "Ver " + (evento.evidencias() == null ? 0 : evento.evidencias().size())
                    + " evidência(s)",
                    "'" + SHEET_EVIDENCIAS + "'!A" + primeira, creationHelper, styles.hyperlink);
            } else {
                write(row, 17, "", styles.celula);
            }

            write(row, 18, rotuloStatusEvento(evento.status()), styles.celula);
            writeStatusSga(row, 19, evento.statusSga(), styles);
            write(row, 20, rotuloNivelCompletude(evento.nivelCompletude()), styles.celula);
            write(row, 21, rotuloFontePrimaria(evento.fontePrimaria()), styles.celula);
            write(row, 22, format(evento.dataDescoberta()), styles.celula);
            write(row, 23, format(evento.dataAtualizacao()), styles.celula);
            write(row, 24, evento.observacoesIa(), styles.celula);
        }

        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, HEADERS_EVENTOS.size() - 1));
        sheet.createFreezePane(1, 1);
    }

    private void preencherAbaResumo(SXSSFSheet sheet, List<Evento> eventos, SectionStyles styles) throws IOException {
        var total = eventos.size();
        var inedito = (int) eventos.stream().filter(e -> e.statusSga() == StatusSGA.INEDITO).count();
        var jaCadastrado = (int) eventos.stream().filter(e -> e.statusSga() == StatusSGA.JA_CADASTRADO).count();
        var totalEvidencias = eventos.stream().mapToInt(e -> e.evidencias() == null ? 0 : e.evidencias().size()).sum();
        var taxaIneditismo = total == 0 ? 0d : (inedito * 100d / total);
        var agora = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        // Larguras uniformes (colunas A-H), conforme template.
        for (var col = 0; col < 8; col++) {
            sheet.setColumnWidth(col, LARGURA_RESUMO * 256);
        }

        // Linha 1: título mesclado A1:H1, bold 24 azul.
        var rowTitulo = sheet.createRow(0);
        var cellTitulo = rowTitulo.createCell(0);
        cellTitulo.setCellValue("CAPTURA DE EVENTOS — RESUMO EXECUTIVO");
        cellTitulo.setCellStyle(styles.titulo);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        // Linha 2: geração mesclada A2:H2, itálico cinza.
        var rowGeracao = sheet.createRow(1);
        var cellGeracao = rowGeracao.createCell(0);
        cellGeracao.setCellValue("Gerado em: " + agora + " — Projeto Digital Mind x ECAD");
        cellGeracao.setCellStyle(styles.geradoEm);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));

        // Linha 3: (vazia)

        // Linha 4: KPIs em linha (B, D, F, H), bold 24.
        var rowKpis = sheet.createRow(3);
        writeKpi(rowKpis, 1, Integer.toString(total), styles.kpi);
        writeKpi(rowKpis, 3, Integer.toString(inedito), styles.kpi);
        writeKpi(rowKpis, 5, Integer.toString(jaCadastrado), styles.kpi);
        writeKpi(rowKpis, 7, String.format(Locale.ROOT, "%.1f%%", taxaIneditismo), styles.kpi);

        // Linha 5: rótulos dos KPIs, cinza.
        var rowRotulos = sheet.createRow(4);
        write(rowRotulos, 1, "Total Capturado", styles.rotuloKpi);
        write(rowRotulos, 3, "Eventos INÉDITOS", styles.rotuloKpi);
        write(rowRotulos, 5, "Já Cadastrados", styles.rotuloKpi);
        write(rowRotulos, 7, "Taxa Ineditismo", styles.rotuloKpi);

        // Linha 6: (vazia)

        // Linha 7: total de evidências, bold vinho.
        var rowEvidencias = sheet.createRow(6);
        writeKpi(rowEvidencias, 1, Integer.toString(totalEvidencias), styles.kpiEvidencias);

        // Linha 8: rótulo, cinza.
        var rowRotuloEvidencias = sheet.createRow(7);
        write(rowRotuloEvidencias, 1, "Total de Evidências Coletadas", styles.rotuloKpi);

        // Linha 10: Distribuição por Status SGA (Deduplicação).
        var rowTituloSga = sheet.createRow(9);
        write(rowTituloSga, 0, "Distribuição por Status SGA (Deduplicação)", styles.tituloSecao);
        sheet.addMergedRegion(new CellRangeAddress(9, 9, 0, 3));
        var headerSga = sheet.createRow(10);
        write(headerSga, 0, "Status SGA", styles.headerTabela);
        write(headerSga, 1, "Quantidade", styles.headerTabela);
        write(headerSga, 2, "% do Total", styles.headerTabela);
        var linha = 11;
        linha = escreverDistribuicao(sheet, linha, "INÉDITO", inedito, total, styles.celulaCentro);
        linha = escreverDistribuicao(sheet, linha, "JÁ CADASTRADO", jaCadastrado, total, styles.celulaCentro);

        // Distribuição por Nível de Completude.
        var linhaTituloNivel = linha + 1;
        var rowTituloNivel = sheet.createRow(linhaTituloNivel);
        write(rowTituloNivel, 0, "Distribuição por Nível de Completude", styles.tituloSecao);
        sheet.addMergedRegion(new CellRangeAddress(linhaTituloNivel, linhaTituloNivel, 0, 3));
        var headerNivel = sheet.createRow(linhaTituloNivel + 1);
        write(headerNivel, 0, "Nível", styles.headerTabela);
        write(headerNivel, 1, "Quantidade", styles.headerTabela);
        write(headerNivel, 2, "% do Total", styles.headerTabela);
        linha = linhaTituloNivel + 2;
        for (var nivel : NivelCompletude.values()) {
            var qtd = (int) eventos.stream().filter(e -> e.nivelCompletude() == nivel).count();
            linha = escreverDistribuicao(sheet, linha, rotuloNivelCompletude(nivel), qtd, total, styles.celula);
        }

        // Distribuição por Tipo de Fonte.
        var linhaTituloFonte = linha + 1;
        var rowTituloFonte = sheet.createRow(linhaTituloFonte);
        write(rowTituloFonte, 0, "Distribuição por Tipo de Fonte", styles.tituloSecao);
        sheet.addMergedRegion(new CellRangeAddress(linhaTituloFonte, linhaTituloFonte, 0, 3));
        var headerFonte = sheet.createRow(linhaTituloFonte + 1);
        write(headerFonte, 0, "Fonte Primária", styles.headerTabela);
        write(headerFonte, 1, "Quantidade", styles.headerTabela);
        write(headerFonte, 2, "% do Total", styles.headerTabela);
        linha = linhaTituloFonte + 2;
        var fontes = eventos.stream()
            .map(Evento::fontePrimaria)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.groupingBy(f -> f, LinkedHashMap::new, java.util.stream.Collectors.counting()));
        for (var entry : fontes.entrySet()) {
            linha = escreverDistribuicao(sheet, linha, rotuloFontePrimaria(entry.getKey()),
                entry.getValue().intValue(), total, styles.celula);
        }
    }

    /** Escreve uma linha de distribuição (rótulo, quantidade, %) e retorna a próxima linha. */
    private static int escreverDistribuicao(SXSSFSheet sheet, int linha, String rotulo, int qtd, int total, CellStyle estilo) {
        var row = sheet.createRow(linha);
        write(row, 0, rotulo, estilo);
        write(row, 1, Integer.toString(qtd), estilo);
        var pct = total == 0 ? 0d : (qtd * 100d / total);
        write(row, 2, String.format(Locale.ROOT, "%.1f%%", pct), estilo);
        return linha + 1;
    }

    private static void writeKpi(Row row, int column, String value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void writeStatusSga(Row row, int column, StatusSGA status, SectionStyles styles) {
        var cell = row.createCell(column);
        cell.setCellValue(rotuloStatusSga(status));
        if (status == StatusSGA.INEDITO) {
            cell.setCellStyle(styles.statusInedito);
        } else if (status == StatusSGA.JA_CADASTRADO) {
            cell.setCellStyle(styles.statusJaCadastrado);
        } else {
            cell.setCellStyle(styles.celula);
        }
    }

    private static String interpretePrincipal(List<String> interpretes) {
        if (interpretes == null || interpretes.isEmpty()) {
            return "";
        }
        return nullToEmpty(interpretes.get(0));
    }

    private static String interpretesOutros(List<String> interpretes) {
        if (interpretes == null || interpretes.size() <= 1) {
            return "";
        }
        return String.join("; ", interpretes.subList(1, interpretes.size()));
    }

    private static String rotuloTipoEvidencia(br.com.ecad.captacao.shared.domain.enums.TipoEvidencia tipo) {
        if (tipo == null) {
            return "";
        }
        return switch (tipo) {
            case CONTRATO_MUSICAL -> "Contrato";
        };
    }

    private static String rotuloTipoMusica(br.com.ecad.captacao.shared.domain.enums.TipoMusica tipo) {
        if (tipo == null) {
            return "Não Identificado";
        }
        return switch (tipo) {
            case AO_VIVO -> "Ao Vivo";
            case MECANICA -> "Mecânica";
            case MISTA -> "Mista";
            case NAO_IDENTIFICADO -> "Não Identificado";
        };
    }

    private static String rotuloCobranca(br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso cobranca) {
        if (cobranca == null) {
            return "Não Identificado";
        }
        return switch (cobranca) {
            case SIM -> "Sim";
            case NAO_GRATUITO -> "Não (Gratuito)";
            case NAO_IDENTIFICADO -> "Não Identificado";
        };
    }

    private static String formatValorIngresso(Double valor, br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso cobranca) {
        if (cobranca == br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso.SIM && valor != null) {
            return String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", valor);
        }
        return "";
    }

    private static String rotuloStatusEvento(br.com.ecad.captacao.shared.domain.enums.StatusEvento status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case AGENDADO -> "Agendado";
            case EM_ANDAMENTO -> "Em Andamento";
            case REALIZADO -> "Realizado";
            case CANCELADO -> "Cancelado";
        };
    }

    private static String rotuloStatusSga(StatusSGA status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case INEDITO -> "INÉDITO";
            case JA_CADASTRADO -> "JÁ CADASTRADO";
            case NAO_VERIFICADO -> "NÃO VERIFICADO";
        };
    }

    private static String rotuloNivelCompletude(br.com.ecad.captacao.shared.domain.enums.NivelCompletude nivel) {
        if (nivel == null) {
            return "";
        }
        return switch (nivel) {
            case ALTO -> "Alto";
            case MEDIO -> "Médio";
            case BASICO -> "Básico";
            case INSUFICIENTE -> "Insuficiente";
        };
    }

    private static String rotuloFontePrimaria(br.com.ecad.captacao.shared.domain.enums.TipoCanal fonte) {
        if (fonte == null) {
            return "";
        }
        return switch (fonte) {
            case AGREGADOR_GOV -> "Agregador Gov (AMUNES)";
        };
    }

    private static void criarHeader(SXSSFSheet sheet, List<String> headers, CellStyle style, int[] larguras) {
        var row = sheet.createRow(0);
        for (var column = 0; column < headers.size(); column++) {
            var cell = row.createCell(column);
            cell.setCellValue(headers.get(column));
            cell.setCellStyle(style);
        }
        if (larguras != null) {
            for (var column = 0; column < larguras.length; column++) {
                sheet.setColumnWidth(column, larguras[column] * 256);
            }
        }
    }

    private static void criarHeaderSecionado(SXSSFSheet sheet, SectionStyles styles) {
        var row = sheet.createRow(0);
        for (var column = 0; column < HEADERS_EVENTOS.size(); column++) {
            var cell = row.createCell(column);
            cell.setCellValue(HEADERS_EVENTOS.get(column));
            cell.setCellStyle(estiloPorColuna(column, styles));
        }
        for (var column = 0; column < LARGURAS_EVENTOS.length; column++) {
            sheet.setColumnWidth(column, LARGURAS_EVENTOS[column] * 256);
        }
    }

    private static CellStyle estiloPorColuna(int column, SectionStyles styles) {
        if (column < FIM_IDENTIFICACAO) {
            return styles.headerIdentificacao;
        }
        if (column < FIM_COMPLEMENTAR) {
            return styles.headerComplementar;
        }
        if (column < FIM_EVIDENCIA) {
            return styles.headerEvidencia;
        }
        return styles.headerInteligencia;
    }

    private static void write(Row row, int column, String value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void write(Row row, int column, int value, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void writeHyperlink(Row row, int column, String url, CreationHelper helper, CellStyle style) {
        var cell = row.createCell(column);
        var safe = url == null ? "" : url.trim();
        cell.setCellValue(safe);
        if (safe.isBlank() || !pareceUrl(safe)) {
            return;
        }
        var hyperlink = helper.createHyperlink(HyperlinkType.URL);
        hyperlink.setAddress(safe);
        cell.setHyperlink(hyperlink);
        cell.setCellStyle(style);
    }

    private static void writeMagicLink(Row row, int column, String label, String address, CreationHelper helper, CellStyle style) {
        var cell = row.createCell(column);
        cell.setCellValue(label);
        var hyperlink = helper.createHyperlink(HyperlinkType.DOCUMENT);
        hyperlink.setAddress(address);
        cell.setHyperlink(hyperlink);
        cell.setCellStyle(style);
    }

    private static boolean pareceUrl(String value) {
        var lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String format(OffsetDateTime value) {
        return value == null ? "" : value.toLocalDate().format(DATA_BR);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Estilos da planilha, replicando a paleta e formatação do template XLSX. */
    private static final class SectionStyles {
        final CellStyle headerIdentificacao;
        final CellStyle headerComplementar;
        final CellStyle headerEvidencia;
        final CellStyle headerInteligencia;
        final CellStyle hyperlink;
        final CellStyle celula;
        final CellStyle celulaCentro;
        final CellStyle statusInedito;
        final CellStyle statusJaCadastrado;
        final CellStyle titulo;
        final CellStyle geradoEm;
        final CellStyle kpi;
        final CellStyle kpiEvidencias;
        final CellStyle rotuloKpi;
        final CellStyle tituloSecao;
        final CellStyle headerTabela;

        private SectionStyles(CellStyle id, CellStyle comp, CellStyle ev, CellStyle intel, CellStyle hyperlink,
            CellStyle celula, CellStyle celulaCentro, CellStyle statusInedito, CellStyle statusJaCadastrado,
            CellStyle titulo, CellStyle geradoEm, CellStyle kpi, CellStyle kpiEvidencias, CellStyle rotuloKpi,
            CellStyle tituloSecao, CellStyle headerTabela) {
            this.headerIdentificacao = id;
            this.headerComplementar = comp;
            this.headerEvidencia = ev;
            this.headerInteligencia = intel;
            this.hyperlink = hyperlink;
            this.celula = celula;
            this.celulaCentro = celulaCentro;
            this.statusInedito = statusInedito;
            this.statusJaCadastrado = statusJaCadastrado;
            this.titulo = titulo;
            this.geradoEm = geradoEm;
            this.kpi = kpi;
            this.kpiEvidencias = kpiEvidencias;
            this.rotuloKpi = rotuloKpi;
            this.tituloSecao = tituloSecao;
            this.headerTabela = headerTabela;
        }

        static SectionStyles build(SXSSFWorkbook workbook) {
            var headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);

            var hyperlinkFont = (XSSFFont) workbook.createFont();
            hyperlinkFont.setUnderline(Font.U_SINGLE);
            hyperlinkFont.setColor(new XSSFColor(COR_HYPERLINK, null));

            var hyperlinkStyle = workbook.createCellStyle();
            hyperlinkStyle.setFont(hyperlinkFont);
            aplicarBorda(hyperlinkStyle);

            var celulaStyle = workbook.createCellStyle();
            aplicarBorda(celulaStyle);
            celulaStyle.setWrapText(true);
            celulaStyle.setVerticalAlignment(VerticalAlignment.TOP);

            var celulaCentroStyle = workbook.createCellStyle();
            celulaCentroStyle.cloneStyleFrom(celulaStyle);
            celulaCentroStyle.setAlignment(HorizontalAlignment.CENTER);

            var statusIneditoStyle = workbook.createCellStyle();
            aplicarBorda(statusIneditoStyle);
            statusIneditoStyle.setFont(fonte(workbook, COR_VERDE_TEXTO, true));
            statusIneditoStyle.setFillForegroundColor(new XSSFColor(COR_FILL_INEDITO, null));
            statusIneditoStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            statusIneditoStyle.setAlignment(HorizontalAlignment.CENTER);

            var statusJaCadastradoStyle = workbook.createCellStyle();
            aplicarBorda(statusJaCadastradoStyle);
            statusJaCadastradoStyle.setFont(fonte(workbook, COR_VERMELHO_TEXTO, true));
            statusJaCadastradoStyle.setFillForegroundColor(new XSSFColor(COR_FILL_JA_CADASTRADO, null));
            statusJaCadastradoStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            statusJaCadastradoStyle.setAlignment(HorizontalAlignment.CENTER);

            var tituloStyle = workbook.createCellStyle();
            tituloStyle.setFont(fonte(workbook, COR_TITULO, true, 24));
            tituloStyle.setAlignment(HorizontalAlignment.CENTER);

            var geradoEmStyle = workbook.createCellStyle();
            var geradoEmFont = (XSSFFont) workbook.createFont();
            geradoEmFont.setItalic(true);
            geradoEmFont.setFontHeightInPoints((short) 10);
            geradoEmFont.setColor(new XSSFColor(COR_GERADO_EM, null));
            geradoEmStyle.setFont(geradoEmFont);
            geradoEmStyle.setAlignment(HorizontalAlignment.CENTER);

            var kpiStyle = workbook.createCellStyle();
            kpiStyle.setFont(fonte(workbook, COR_KPI, true, 24));
            kpiStyle.setAlignment(HorizontalAlignment.CENTER);

            var kpiEvidenciasStyle = workbook.createCellStyle();
            kpiEvidenciasStyle.setFont(fonte(workbook, COR_KPI_EVIDENCIAS, true, 20));
            kpiEvidenciasStyle.setAlignment(HorizontalAlignment.CENTER);

            var rotuloKpiStyle = workbook.createCellStyle();
            rotuloKpiStyle.setFont(fonte(workbook, COR_ROTULO_KPI, false, 11));
            rotuloKpiStyle.setAlignment(HorizontalAlignment.CENTER);

            var tituloSecaoStyle = workbook.createCellStyle();
            tituloSecaoStyle.setFont(fonte(workbook, COR_TITULO, true, 14));
            tituloSecaoStyle.setAlignment(HorizontalAlignment.CENTER);

            var headerTabelaStyle = workbook.createCellStyle();
            headerTabelaStyle.setFont(headerFont);
            headerTabelaStyle.setFillForegroundColor(new XSSFColor(COR_TABELA_HEADER, null));
            headerTabelaStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            aplicarBorda(headerTabelaStyle);
            headerTabelaStyle.setAlignment(HorizontalAlignment.CENTER);
            headerTabelaStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            return new SectionStyles(
                buildHeaderStyle(workbook, COR_IDENTIFICACAO, headerFont),
                buildHeaderStyle(workbook, COR_COMPLEMENTAR, headerFont),
                buildHeaderStyle(workbook, COR_EVIDENCIA, headerFont),
                buildHeaderStyle(workbook, COR_INTELIGENCIA, headerFont),
                hyperlinkStyle,
                celulaStyle,
                celulaCentroStyle,
                statusIneditoStyle,
                statusJaCadastradoStyle,
                tituloStyle,
                geradoEmStyle,
                kpiStyle,
                kpiEvidenciasStyle,
                rotuloKpiStyle,
                tituloSecaoStyle,
                headerTabelaStyle);
        }

        private static Font fonte(SXSSFWorkbook workbook, byte[] rgb, boolean bold) {
            return fonte(workbook, rgb, bold, 11);
        }

        private static Font fonte(SXSSFWorkbook workbook, byte[] rgb, boolean bold, int tamanho) {
            var font = (XSSFFont) workbook.createFont();
            font.setColor(new XSSFColor(rgb, null));
            font.setBold(bold);
            font.setFontHeightInPoints((short) tamanho);
            return font;
        }

        private static void aplicarBorda(CellStyle style) {
            var xssf = (XSSFCellStyle) style;
            xssf.setBorderTop(BorderStyle.THIN);
            xssf.setBorderBottom(BorderStyle.THIN);
            xssf.setBorderLeft(BorderStyle.THIN);
            xssf.setBorderRight(BorderStyle.THIN);
            var cor = new XSSFColor(COR_BORDA, null);
            xssf.setTopBorderColor(cor);
            xssf.setBottomBorderColor(cor);
            xssf.setLeftBorderColor(cor);
            xssf.setRightBorderColor(cor);
        }

        private static CellStyle buildHeaderStyle(SXSSFWorkbook workbook, byte[] rgb, Font font) {
            var style = (XSSFCellStyle) workbook.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(rgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            aplicarBorda(style);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            return style;
        }
    }
}
