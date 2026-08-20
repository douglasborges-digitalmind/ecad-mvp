package br.com.ecad.captacao.controlcenter.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import br.com.ecad.captacao.controlcenter.models.SetupPncpUrlsRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PncpUrlSetupServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void setupShouldReadLegacyWindows1252Csv() throws Exception {
        var csv = tempDir.resolve("cnpjs-legacy.csv");
        var output = tempDir.resolve("resultado_pncp.csv");
        Files.writeString(csv, "cnpj;munic\u00edpio\n12.345.678/0001-90;Teres\u00f3polis/RJ\n", Charset.forName("windows-1252"));

        var service = new PncpUrlSetupService(stubCatalog("Teresópolis", "RJ", "RIO DE JANEIRO", "3305802", "https://pncp.gov.br/app/contratos?pagina=1&municipios=3305802&status=vigente&ufs=RJ"));
        var result = service.setup(new SetupPncpUrlsRequest(csv.toString(), output.toString(), null, "show cache", 0.0d));

        assertEquals(1, result.processadas());
        assertEquals(1, result.sucessos());
        assertEquals(0, result.erros());
        assertEquals("Teres\u00f3polis", result.resultados().getFirst().municipio());
        assertEquals("RJ", result.resultados().getFirst().uf());
        assertEquals("RIO DE JANEIRO", result.resultados().getFirst().unidadeEcad());

        var generated = Files.readString(output);
        assertTrue(generated.contains("Teres\u00f3polis"));
        assertTrue(generated.contains("RJ"));
        assertTrue(generated.contains("RIO DE JANEIRO"));
        assertTrue(generated.contains("municipios=3305802"));
        assertEquals(generated, result.conteudoCsvSaida());
    }

    @Test
    void setupShouldAcceptInlineBase64PayloadWithoutFilesystemOutput() throws Exception {
        var csvBytes = "cnpj,municipio,uf\n12.345.678/0001-90,Teresopolis,RJ\n".getBytes(Charset.forName("UTF-8"));

        var service = new PncpUrlSetupService(stubCatalog("Teresópolis", "RJ", "RIO DE JANEIRO", "3305802", "https://pncp.gov.br/app/contratos?pagina=1&municipios=3305802&status=vigente&ufs=RJ"));
        var result = service.setup(new SetupPncpUrlsRequest(null, null, Base64.getEncoder().encodeToString(csvBytes), "show cache", 0.0d));

        assertEquals("payload:base64", result.arquivoCsvEntrada());
        assertNull(result.arquivoCsvSaida());
        assertEquals(1, result.processadas());
        assertEquals(1, result.sucessos());
        assertEquals("RJ", result.resultados().getFirst().uf());
        assertEquals("RIO DE JANEIRO", result.resultados().getFirst().unidadeEcad());
        assertTrue(result.conteudoCsvSaida().contains("municipios=3305802"));
    }

    private static PncpMunicipiosCatalog stubCatalog(String municipio, String uf, String unidadeEcad, String idPncp, String url) {
        var item = new PncpMunicipiosCatalog.PncpMunicipioCatalogItem(municipio, uf, unidadeEcad, idPncp, url);
        return new PncpMunicipiosCatalog() {
            @Override
            public Optional<PncpMunicipioCatalogItem> find(String requestedMunicipio, String requestedUf) {
                return Optional.of(item);
            }

            @Override
            public List<PncpMunicipioCatalogItem> listAll() {
                return List.of(item);
            }
        };
    }
}