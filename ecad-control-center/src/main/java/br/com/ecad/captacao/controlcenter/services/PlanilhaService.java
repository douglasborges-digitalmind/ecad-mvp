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
    private static final String SHEET_EVIDENCIAS = "Evidências";
    private static final String SHEET_RESUMO = "Resumo Executivo";

    private static final byte[] COR_IDENTIFICACAO = new byte[] {(byte) 31, (byte) 78, (byte) 121};
    private static final byte[] COR_COMPLEMENTAR = new byte[] {(byte) 27, (byte) 94, (byte) 87};
    private static final byte[] COR_EVIDENCIA = new byte[] {(byte) 132, (byte) 32, (byte) 41};
    private static final byte[] COR_INTELIGENCIA = new byte[] {(byte) 76, (byte) 28, (byte) 110};

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
        criarHeader(sheet, HEADERS_EVIDENCIAS, styles.headerEvidencia);

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
                write(row, 0, evento.codigoEvento());
                write(row, 1, evidencia.sequencia());
                write(row, 2, rotuloTipoEvidencia(evidencia.tipo()));
                writeHyperlink(row, 3, evidencia.urlOrigem(), creationHelper, styles.hyperlink);
                writeHyperlink(row, 4, evidencia.urlArmazenamentoInterno(), creationHelper, styles.hyperlink);
                write(row, 5, format(evidencia.dataCaptura()));
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
            write(row, 0, evento.codigoEvento());
            write(row, 1, evento.titulo());
            write(row, 2, format(evento.dataInicio()));
            write(row, 3, evento.hora());
            write(row, 4, evento.local());
            write(row, 5, evento.municipio());
            write(row, 6, evento.uf());
            write(row, 7, evento.unidadeEcad());
            write(row, 8, evento.promotorNome());
            write(row, 9, evento.promotorContato());
            write(row, 10, interpretePrincipal(evento.interpretes()));
            write(row, 11, interpretesOutros(evento.interpretes()));
            write(row, 12, rotuloTipoMusica(evento.tipoMusica()));
            write(row, 13, rotuloCobranca(evento.cobrancaIngresso()));
            write(row, 14, formatValorIngresso(evento.valorIngresso(), evento.cobrancaIngresso()));
            write(row, 15, evento.capacidadePublico() == null ? "" : evento.capacidadePublico().toString());
            write(row, 16, evento.evidencias() == null ? 0 : evento.evidencias().size());

            var primeira = primeiraLinhaEvidencia.get(nullToEmpty(evento.codigoEvento()));
            if (primeira != null) {
                writeMagicLink(row, 17, "Ver " + (evento.evidencias() == null ? 0 : evento.evidencias().size())
                    + " evidência(s) (linha " + primeira + ")",
                    "'" + SHEET_EVIDENCIAS + "'!A" + primeira, creationHelper, styles.hyperlink);
            } else {
                write(row, 17, "");
            }

            write(row, 18, rotuloStatusEvento(evento.status()));
            write(row, 19, rotuloStatusSga(evento.statusSga()));
            write(row, 20, rotuloNivelCompletude(evento.nivelCompletude()));
            write(row, 21, rotuloFontePrimaria(evento.fontePrimaria()));
            write(row, 22, format(evento.dataDescoberta()));
            write(row, 23, format(evento.dataAtualizacao()));
            write(row, 24, evento.observacoesIa());
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

        // Linha 1: título
        var rowTitulo = sheet.createRow(0);
        var cellTitulo = rowTitulo.createCell(1);
        cellTitulo.setCellValue("CAPTURA DE EVENTOS — RESUMO EXECUTIVO");
        cellTitulo.setCellStyle(styles.headerIdentificacao);

        // Linha 2: geração
        var rowGeracao = sheet.createRow(1);
        var cellGeracao = rowGeracao.createCell(1);
        cellGeracao.setCellValue("Gerado em: " + agora + " — Projeto Digital Mind x ECAD");

        // Linha 3: (vazia)

        // Linha 4: KPIs em linha (B, D, F, H)
        var rowKpis = sheet.createRow(3);
        write(rowKpis, 1, Integer.toString(total));
        write(rowKpis, 3, Integer.toString(inedito));
        write(rowKpis, 5, Integer.toString(jaCadastrado));
        write(rowKpis, 7, String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", taxaIneditismo));

        // Linha 5: rótulos dos KPIs
        var rowRotulos = sheet.createRow(4);
        write(rowRotulos, 1, "Total Capturado");
        write(rowRotulos, 3, "Eventos INÉDITOS");
        write(rowRotulos, 5, "Já Cadastrados");
        write(rowRotulos, 7, "Taxa Ineditismo");

        // Linha 6: (vazia)

        // Linha 7: total de evidências
        var rowEvidencias = sheet.createRow(6);
        write(rowEvidencias, 1, Integer.toString(totalEvidencias));

        // Linha 8: rótulo
        var rowRotuloEvidencias = sheet.createRow(7);
        write(rowRotuloEvidencias, 1, "Total de Evidências Coletadas");

        sheet.setColumnWidth(0, 4_000);
        sheet.setColumnWidth(1, 6_000);
        sheet.setColumnWidth(3, 6_000);
        sheet.setColumnWidth(5, 6_000);
        sheet.setColumnWidth(7, 6_000);
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
