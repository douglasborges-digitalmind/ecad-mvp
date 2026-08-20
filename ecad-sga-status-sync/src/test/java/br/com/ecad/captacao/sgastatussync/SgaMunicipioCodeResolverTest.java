package br.com.ecad.captacao.sgastatussync;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SgaMunicipioCodeResolverTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void resolvesMunicipioCodeFromCsv() throws Exception {
        var csv = tempDir.resolve("municipios.csv");
        Files.writeString(csv, "UF,MUNIC\u00cdPIO,COD_MUNICIPIOECAD\nSP,S\u00e3o Paulo,3550308\nRJ,Niter\u00f3i,3303302\n");
        var codes = SgaMunicipioCodeResolver.loadCodes(csv.toString());

        assertThat(codes).containsEntry(new SgaMunicipioCodeResolver.Key("SP", "SAO PAULO"), 3550308);
        assertThat(codes).containsEntry(new SgaMunicipioCodeResolver.Key("RJ", "NITEROI"), 3303302);
    }

    @Test
    void splitsQuotedCsvColumns() {
        assertThat(SgaMunicipioCodeResolver.splitCsvLine("SP,\"Sao Paulo, Capital\",3550308"))
            .containsExactly("SP", "Sao Paulo, Capital", "3550308");
    }
}
