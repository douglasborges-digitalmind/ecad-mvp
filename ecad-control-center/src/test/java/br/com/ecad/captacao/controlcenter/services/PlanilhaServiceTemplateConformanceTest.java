package br.com.ecad.captacao.controlcenter.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.Evidencia;
import br.com.ecad.captacao.shared.domain.entities.Evento;
import br.com.ecad.captacao.shared.domain.enums.CobrancaIngresso;
import br.com.ecad.captacao.shared.domain.enums.NivelCompletude;
import br.com.ecad.captacao.shared.domain.enums.StatusEvento;
import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.enums.TipoCanal;
import br.com.ecad.captacao.shared.domain.enums.TipoEvidencia;
import br.com.ecad.captacao.shared.domain.enums.TipoMusica;
import br.com.ecad.captacao.shared.infrastructure.repositories.DestinatarioRepository;
import br.com.ecad.captacao.shared.infrastructure.repositories.EventoRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Valida que a planilha gerada por {@link PlanilhaService} bate com o template
 * Template-Planilha-Eventos-ECAD.xlsx: nomes de abas, cabeçalhos das abas
 * "Eventos Capturados" e "Evidências", e layout do "Resumo Executivo".
 */
class PlanilhaServiceTemplateConformanceTest {

    private static final List<String> HEADERS_EVENTOS_TEMPLATE = List.of(
        "ID_Evento", "Titulo_Evento", "Data_Realizacao", "Hora_Inicio", "Local_Evento", "Municipio", "UF",
        "Unidade_ECAD", "Promotor", "Contato_Promotor", "Interprete_Principal", "Interpretes_Outros",
        "Tipo_Musica", "Cobranca_Ingresso", "Valor_Ingresso", "Capacidade_Publico", "Qtd_Evidencias",
        "Ver_Evidencias", "Status_Evento", "Status_SGA", "Nivel_Completude", "Fonte_Primaria",
        "Data_Descoberta", "Data_Atualizacao", "Observacoes_IA");

    private static final List<String> HEADERS_EVIDENCIAS_TEMPLATE = List.of(
        "ID_Evento", "Seq", "Tipo_Evidencia", "URL_Fonte", "URL_Blob_Storage", "Data_Captura");

    @Test
    void planilhaGeradaDeveConterAbasNomesDoTemplate() throws Exception {
        try (var workbook = gerarPlanilhaVazia()) {
            var nomes = new java.util.ArrayList<String>();
            workbook.sheetIterator().forEachRemaining(sheet -> nomes.add(sheet.getSheetName()));
            assertEquals(List.of("Eventos Capturados", "Evidências", "Resumo Executivo"), nomes);
        }
    }

    @Test
    void abaEventosDeveTerCabeçalhosDoTemplate() throws Exception {
        try (var workbook = gerarPlanilhaVazia()) {
            var sheet = workbook.getSheet("Eventos Capturados");
            var headerRow = sheet.getRow(0);
            var actual = new java.util.ArrayList<String>();
            for (var c = 0; c < headerRow.getLastCellNum(); c++) {
                actual.add(headerRow.getCell(c).getStringCellValue());
            }
            assertEquals(HEADERS_EVENTOS_TEMPLATE, actual);
        }
    }

    @Test
    void abaEvidenciasDeveTerCabeçalhosDoTemplate() throws Exception {
        try (var workbook = gerarPlanilhaVazia()) {
            var sheet = workbook.getSheet("Evidências");
            var headerRow = sheet.getRow(0);
            var actual = new java.util.ArrayList<String>();
            for (var c = 0; c < headerRow.getLastCellNum(); c++) {
                actual.add(headerRow.getCell(c).getStringCellValue());
            }
            assertEquals(HEADERS_EVIDENCIAS_TEMPLATE, actual);
        }
    }

    @Test
    void abaResumoDeveTerLayoutHorizontalDoTemplate() throws Exception {
        try (var workbook = gerarPlanilhaVazia()) {
            var sheet = workbook.getSheet("Resumo Executivo");
            // Linha 1 (index 0): título em B1
            assertEquals("CAPTURA DE EVENTOS — RESUMO EXECUTIVO", sheet.getRow(0).getCell(1).getStringCellValue());
            // Linha 2 (index 1): "Gerado em: ..." em B2
            var geracao = sheet.getRow(1).getCell(1).getStringCellValue();
            assertEquals(true, geracao.startsWith("Gerado em: "));
            // Linha 4 (index 3): KPIs em B, D, F, H
            assertEquals("0", sheet.getRow(3).getCell(1).getStringCellValue()); // total
            assertEquals("0", sheet.getRow(3).getCell(3).getStringCellValue()); // inéditos
            assertEquals("0", sheet.getRow(3).getCell(5).getStringCellValue()); // já cadastrados
            assertEquals("0,0%", sheet.getRow(3).getCell(7).getStringCellValue()); // taxa
            // Linha 5 (index 4): rótulos
            assertEquals("Total Capturado", sheet.getRow(4).getCell(1).getStringCellValue());
            assertEquals("Eventos INÉDITOS", sheet.getRow(4).getCell(3).getStringCellValue());
            assertEquals("Já Cadastrados", sheet.getRow(4).getCell(5).getStringCellValue());
            assertEquals("Taxa Ineditismo", sheet.getRow(4).getCell(7).getStringCellValue());
            // Linha 7 (index 6): total evidências
            assertEquals("0", sheet.getRow(6).getCell(1).getStringCellValue());
            // Linha 8 (index 7): rótulo
            assertEquals("Total de Evidências Coletadas", sheet.getRow(7).getCell(1).getStringCellValue());
        }
    }

    @Test
    void eventoComEvidenciasDeveGerarLinhasCorrespondentes() throws Exception {
        var evento = eventoExemplo();
        var service = newService(List.of(evento));
        var bytes = service.gerarPlanilha();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheetEventos = workbook.getSheet("Eventos Capturados");
            assertEquals(2, sheetEventos.getPhysicalNumberOfRows()); // header + 1 evento
            var row = sheetEventos.getRow(1);
            assertEquals("EVT-2026-0001", row.getCell(0).getStringCellValue());
            assertEquals("Festa Teste", row.getCell(1).getStringCellValue());
            assertEquals("Unidade Vitória", row.getCell(7).getStringCellValue());
            assertEquals("Prefeitura X", row.getCell(8).getStringCellValue());
            assertEquals("(27) 1234-5678", row.getCell(9).getStringCellValue());
            assertEquals("Banda A", row.getCell(10).getStringCellValue());
            assertEquals("Banda B; Banda C", row.getCell(11).getStringCellValue());
            assertEquals("Ao Vivo", row.getCell(12).getStringCellValue());
            assertEquals("Sim", row.getCell(13).getStringCellValue());
            assertEquals("R$ 50,00", row.getCell(14).getStringCellValue());
            assertEquals("INÉDITO", row.getCell(19).getStringCellValue());
            assertEquals("Alto", row.getCell(20).getStringCellValue());

            var sheetEvidencias = workbook.getSheet("Evidências");
            assertEquals(2, sheetEvidencias.getPhysicalNumberOfRows()); // header + 1 evidência
            var evRow = sheetEvidencias.getRow(1);
            assertEquals("EVT-2026-0001", evRow.getCell(0).getStringCellValue());
            assertEquals(1, (int) evRow.getCell(1).getNumericCellValue());
            assertEquals("Contrato", evRow.getCell(2).getStringCellValue());
        }
    }

    private XSSFWorkbook gerarPlanilhaVazia() throws Exception {
        var service = newService(List.of());
        var bytes = service.gerarPlanilha();
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private PlanilhaService newService(List<Evento> eventos) throws java.io.IOException {
        var eventoRepo = mock(EventoRepository.class);
        when(eventoRepo.listarParaPlanilha()).thenReturn(eventos);
        var destinatarioRepo = mock(DestinatarioRepository.class);
        when(destinatarioRepo.listar()).thenReturn(List.of());
        var emailService = mock(EmailService.class);
        return new PlanilhaService(eventoRepo, destinatarioRepo, emailService);
    }

    private static Evento eventoExemplo() {
        var agora = OffsetDateTime.now(ZoneOffset.UTC);
        return new Evento(
            UUID.randomUUID(), "EVT-2026-0001", "Festa Teste", agora, null,
            "Praça Central", "Vitória", "ES", "Unidade Vitória", "20:00",
            "12.345.678/0001-90", "Prefeitura X", "(27) 1234-5678",
            List.of("Banda A", "Banda B", "Banda C"),
            TipoMusica.AO_VIVO, CobrancaIngresso.SIM, 50.0, 1000,
            StatusEvento.AGENDADO, StatusSGA.INEDITO, NivelCompletude.ALTO,
            TipoCanal.AGREGADOR_GOV, agora, agora, "Observação de teste",
            null, List.of(new Evidencia(1, TipoEvidencia.CONTRATO_MUSICAL,
                "https://exemplo.com/contrato.pdf", "https://blob/contrato.pdf",
                agora, "hash123", null, null, null)));
    }
}
