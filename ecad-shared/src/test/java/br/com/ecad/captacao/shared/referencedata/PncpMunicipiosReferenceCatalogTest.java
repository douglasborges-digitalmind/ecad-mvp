package br.com.ecad.captacao.shared.referencedata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PncpMunicipiosReferenceCatalogTest {
    @Test
    void shouldResolveMunicipioUsingCanonicalJsonCatalog() {
        var result = PncpMunicipiosReferenceCatalog.tryResolve("Aracaju", "SE");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().idPncp()).isEqualTo("1755");
        assertThat(result.orElseThrow().url()).contains("municipios=1755");
        // unidadeEcad e a unidade regional do ECAD responsavel pela cidade, nao a UF geografica.
        // Aracaju/SE e atendida pela unidade BAHIA (SUBA) conforme fontesPNCP.csv.
        assertThat(result.orElseThrow().unidadeEcad()).isEqualTo("BAHIA");
    }

    @Test
    void shouldIgnoreEntriesWithoutPncpUrl() {
        assertThat(PncpMunicipiosReferenceCatalog.tryResolve("Barra do Ouro", "TO")).isEmpty();
    }
}