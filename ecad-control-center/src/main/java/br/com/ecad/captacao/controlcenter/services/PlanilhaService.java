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
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.infrastructure.repositories.DestinatarioRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
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
    private static final String SHEET_EVIDENCIAS = "Evidencias";
    private static final String SHEET_RESUMO = "Resumo Executivo";

    private static final byte[] COR_IDENTIFICACAO = new byte[] {(byte) 31, (byte) 78, (byte) 121};
    private static final byte[] COR_COMPLEMENTAR = new byte[] {(byte) 27, (byte) 94, (byte) 87};
    private static final byte[] COR_EVIDENCIA = new byte[] {(byte) 132, (byte) 32, (byte) 41};
    private static final byte[] COR_INTELIGENCIA = new byte[] {(byte) 76, (byte) 28, (byte) 110};

    private static final List<String> HEADERS_EVENTOS = List.of(
        "Codigo_Evento", "Titulo", "Data_Inicio", "Data_Termino", "Local", "Municipio", "UF", "Unidade_ECAD", "Hora",
        "Promotor_Nome", "Promotor_CNPJ", "Interpretes", "Tipo_Musica", "Capacidade_Publico",
        "Fonte_Primaria", "Total_Evidencias", "Ver_Evidencias",
        "Status", "Status_SGA", "Nivel_Completude", "Observacoes_IA");
    private static final int FIM_IDENTIFICACAO = 9;
    private static final int FIM_COMPLEMENTAR = 14;
    private static final int FIM_EVIDENCIA = 17;

    private static final List<String> HEADERS_EVIDENCIAS = List.of(
        "ID_Evento", "Codigo_Evento", "Seq", "Tipo_Evidencia", "URL_Origem",
        "URL_Armazenamento_Interno", "Data_Captura", "Hash_Arquivo");

    private final EventoRepository eventoRepository;
    private final DestinatarioRepository destinatarioRepository;
    private final FonteCaptacaoRepository fonteCaptacaoRepository;
    private final EmailService emailService;

    public PlanilhaService(
        EventoRepository eventoRepository,
        DestinatarioRepository destinatarioRepository,
        FonteCaptacaoRepository fonteCaptacaoRepository,
        EmailService emailService) {
        this.eventoRepository = eventoRepository;
        this.destinatarioRepository = destinatarioRepository;
        this.fonteCaptacaoRepository = fonteCaptacaoRepository;
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

            var primeiraLinhaPorEvento = criarAbaEvidencias(workbook, eventosOrdenados, styles);
            criarAbaEventos(workbook, eventosOrdenados, styles, primeiraLinhaPorEvento);
            criarAbaResumo(workbook, eventosOrdenados, styles);

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

    private Map<String, Integer> criarAbaEvidencias(SXSSFWorkbook workbook, List<Evento> eventos, SectionStyles styles) {
        var sheet = workbook.createSheet(SHEET_EVIDENCIAS);
        criarHeader(sheet, HEADERS_EVIDENCIAS, styles.headerEvidencia);

        var primeiraLinha = new LinkedHashMap<String, Integer>();
        var creationHelper = workbook.getCreationHelper();
        var rowIndex = 1;

        for (var evento : eventos) {
            if (evento.evidencias() == null || evento.evidencias().isEmpty()) {
                continue;
            }

            primeiraLinha.put(nullToEmpty(evento.codigoEvento()), rowIndex + 1);
            for (var evidencia : evento.evidencias()) {
                var row = sheet.createRow(rowIndex++);
                write(row, 0, evento.id() == null ? "" : evento.id().toString());
                write(row, 1, evento.codigoEvento());
                write(row, 2, evidencia.sequencia());
                write(row, 3, evidencia.tipo() == null ? "" : evidencia.tipo().jsonValue());
                writeHyperlink(row, 4, evidencia.urlOrigem(), creationHelper, styles.hyperlink);
                writeHyperlink(row, 5, evidencia.urlArmazenamentoInterno(), creationHelper, styles.hyperlink);
                write(row, 6, format(evidencia.dataCaptura()));
                write(row, 7, evidencia.hashArquivo());
            }
        }

        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, HEADERS_EVIDENCIAS.size() - 1));
        sheet.createFreezePane(0, 1);
        return primeiraLinha;
    }

    private void criarAbaEventos(
        SXSSFWorkbook workbook,
        List<Evento> eventos,
        SectionStyles styles,
        Map<String, Integer> primeiraLinhaEvidencia) {
        var sheet = workbook.createSheet(SHEET_EVENTOS);
        criarHeaderSecionado(sheet, styles);

        var creationHelper = workbook.getCreationHelper();
        var rowIndex = 1;
        for (var evento : eventos) {
            var row = sheet.createRow(rowIndex++);
            write(row, 0, evento.codigoEvento());
            write(row, 1, evento.titulo());
            write(row, 2, format(evento.dataInicio()));
            write(row, 3, format(evento.dataTermino()));
            write(row, 4, evento.local());
            write(row, 5, evento.municipio());
            write(row, 6, evento.uf());
            write(row, 7, evento.unidadeEcad());
            write(row, 8, evento.hora());

            write(row, 9, evento.promotorNome());
            write(row, 10, evento.promotorCnpj());
            write(row, 11, evento.interpretes() == null ? "" : String.join("; ", evento.interpretes()));
            write(row, 12, evento.tipoMusica() == null ? "" : evento.tipoMusica().jsonValue());
            write(row, 13, evento.capacidadePublico() == null ? "" : evento.capacidadePublico().toString());

            write(row, 14, evento.fontePrimaria() == null ? "" : evento.fontePrimaria().jsonValue());
            write(row, 15, evento.evidencias() == null ? 0 : evento.evidencias().size());

            var primeira = primeiraLinhaEvidencia.get(nullToEmpty(evento.codigoEvento()));
            if (primeira != null) {
                writeMagicLink(row, 16, "Ver evidencias (linha " + primeira + ")",
                    "'" + SHEET_EVIDENCIAS + "'!A" + primeira, creationHelper, styles.hyperlink);
            } else {
                write(row, 16, "");
            }

            write(row, 17, evento.status() == null ? "" : evento.status().jsonValue());
            write(row, 18, evento.statusSga() == null ? "" : evento.statusSga().jsonValue());
            write(row, 19, evento.nivelCompletude() == null ? "" : evento.nivelCompletude().jsonValue());
            write(row, 20, evento.observacoesIa());
        }

        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, HEADERS_EVENTOS.size() - 1));
        sheet.createFreezePane(1, 1);
    }

    private void criarAbaResumo(SXSSFWorkbook workbook, List<Evento> eventos, SectionStyles styles) throws IOException {
        var sheet = workbook.createSheet(SHEET_RESUMO);
        var fontes = fonteCaptacaoRepository.listar(null, null);

        var total = eventos.size();
        var inedito = (int) eventos.stream().filter(e -> e.statusSga() == StatusSGA.INEDITO).count();
        var jaCadastrado = (int) eventos.stream().filter(e -> e.statusSga() == StatusSGA.JA_CADASTRADO).count();
        var naoVerificado = total - inedito - jaCadastrado;
        var totalEvidencias = eventos.stream().mapToInt(e -> e.evidencias() == null ? 0 : e.evidencias().size()).sum();
        var taxaIneditismo = total == 0 ? 0d : (inedito * 100d / total);

        var rowIndex = 0;
        rowIndex = writeSecaoTitulo(sheet, rowIndex, "Indicadores Gerais", styles.headerIdentificacao);
        rowIndex = writeMetrica(sheet, rowIndex, "Total de Eventos Capturados", Integer.toString(total));
        rowIndex = writeMetrica(sheet, rowIndex, "Eventos INEDITOS", Integer.toString(inedito));
        rowIndex = writeMetrica(sheet, rowIndex, "Eventos JA CADASTRADOS", Integer.toString(jaCadastrado));
        rowIndex = writeMetrica(sheet, rowIndex, "Eventos NAO VERIFICADOS", Integer.toString(naoVerificado));
        rowIndex = writeMetrica(sheet, rowIndex, "Taxa de Ineditismo (%)", String.format(Locale.ROOT, "%.2f", taxaIneditismo));
        rowIndex = writeMetrica(sheet, rowIndex, "Total de Evidencias Coletadas", Integer.toString(totalEvidencias));
        rowIndex = writeMetrica(sheet, rowIndex, "Fontes Cadastradas", Integer.toString(fontes.size()));
        rowIndex++;

        rowIndex = writeSecaoTitulo(sheet, rowIndex, "Distribuicao por Unidade ECAD", styles.headerComplementar);
        rowIndex = writeDistribuicao(sheet, rowIndex, distribuirPorUnidade(eventos));
        rowIndex++;

        rowIndex = writeSecaoTitulo(sheet, rowIndex, "Distribuicao por Fonte Primaria", styles.headerInteligencia);
        writeDistribuicao(sheet, rowIndex, distribuirPorFontePrimaria(eventos));

        sheet.setColumnWidth(0, 12_000);
        sheet.setColumnWidth(1, 6_000);
    }

    private static Map<String, Integer> distribuirPorUnidade(List<Evento> eventos) {
        var resultado = new LinkedHashMap<String, Integer>();
        eventos.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                e -> nullToEmptyOrUnknown(e.unidadeEcad()),
                java.util.stream.Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> resultado.put(entry.getKey(), entry.getValue().intValue()));
        return resultado;
    }

    private static Map<String, Integer> distribuirPorFontePrimaria(List<Evento> eventos) {
        var resultado = new LinkedHashMap<String, Integer>();
        eventos.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.fontePrimaria() == null ? "(nao informada)" : e.fontePrimaria().jsonValue(),
                java.util.stream.Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> resultado.put(entry.getKey(), entry.getValue().intValue()));
        return resultado;
    }

    private static int writeSecaoTitulo(SXSSFSheet sheet, int rowIndex, String titulo, CellStyle style) {
        var row = sheet.createRow(rowIndex);
        var cell = row.createCell(0);
        cell.setCellValue(titulo);
        cell.setCellStyle(style);
        return rowIndex + 1;
    }

    private static int writeMetrica(SXSSFSheet sheet, int rowIndex, String nome, String valor) {
        var row = sheet.createRow(rowIndex);
        write(row, 0, nome);
        write(row, 1, valor);
        return rowIndex + 1;
    }

    private static int writeDistribuicao(SXSSFSheet sheet, int rowIndex, Map<String, Integer> distribuicao) {
        for (var entry : distribuicao.entrySet()) {
            var row = sheet.createRow(rowIndex++);
            write(row, 0, entry.getKey());
            write(row, 1, entry.getValue());
        }
        return rowIndex;
    }

    private static void criarHeader(SXSSFSheet sheet, List<String> headers, CellStyle style) {
        var row = sheet.createRow(0);
        for (var column = 0; column < headers.size(); column++) {
            var cell = row.createCell(column);
            cell.setCellValue(headers.get(column));
            cell.setCellStyle(style);
        }
        for (var column = 0; column < headers.size(); column++) {
            sheet.setColumnWidth(column, larguraDefault(headers.get(column)));
        }
    }

    private static void criarHeaderSecionado(SXSSFSheet sheet, SectionStyles styles) {
        var row = sheet.createRow(0);
        for (var column = 0; column < HEADERS_EVENTOS.size(); column++) {
            var cell = row.createCell(column);
            cell.setCellValue(HEADERS_EVENTOS.get(column));
            cell.setCellStyle(estiloPorColuna(column, styles));
        }
        for (var column = 0; column < HEADERS_EVENTOS.size(); column++) {
            sheet.setColumnWidth(column, larguraDefault(HEADERS_EVENTOS.get(column)));
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

    private static int larguraDefault(String header) {
        var base = header.length() * 280;
        return Math.max(3_500, Math.min(base, 12_000));
    }

    private static void write(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static void write(Row row, int column, int value) {
        row.createCell(column).setCellValue(value);
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

    private static String nullToEmptyOrUnknown(String value) {
        return (value == null || value.isBlank()) ? "(sem unidade)" : value;
    }

    private static final class SectionStyles {
        final CellStyle headerIdentificacao;
        final CellStyle headerComplementar;
        final CellStyle headerEvidencia;
        final CellStyle headerInteligencia;
        final CellStyle hyperlink;

        private SectionStyles(CellStyle id, CellStyle comp, CellStyle ev, CellStyle intel, CellStyle hyperlink) {
            this.headerIdentificacao = id;
            this.headerComplementar = comp;
            this.headerEvidencia = ev;
            this.headerInteligencia = intel;
            this.hyperlink = hyperlink;
        }

        static SectionStyles build(SXSSFWorkbook workbook) {
            var headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);

            var hyperlinkFont = workbook.createFont();
            hyperlinkFont.setUnderline(Font.U_SINGLE);
            hyperlinkFont.setColor(IndexedColors.BLUE.getIndex());

            var hyperlinkStyle = workbook.createCellStyle();
            hyperlinkStyle.setFont(hyperlinkFont);

            return new SectionStyles(
                buildHeaderStyle(workbook, COR_IDENTIFICACAO, headerFont),
                buildHeaderStyle(workbook, COR_COMPLEMENTAR, headerFont),
                buildHeaderStyle(workbook, COR_EVIDENCIA, headerFont),
                buildHeaderStyle(workbook, COR_INTELIGENCIA, headerFont),
                hyperlinkStyle);
        }

        private static CellStyle buildHeaderStyle(SXSSFWorkbook workbook, byte[] rgb, Font font) {
            var style = (XSSFCellStyle) workbook.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(rgb, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }
    }
}
