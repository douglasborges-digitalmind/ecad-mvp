package br.com.ecad.captacao.shared.referencedata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MunicipioUnidadeReferenceCatalogTest {
    @Test
    void getAllShouldLoadSameNumberOfReferenceRowsAsDotNetBaseline() {
        assertThat(MunicipioUnidadeReferenceCatalog.getAll()).hasSize(5085);
    }

    @Test
    void tryResolveShouldNormalizeMunicipioAndUf() {
        var result = MunicipioUnidadeReferenceCatalog.tryResolve("salvador", "ba");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().municipio).isEqualTo("Salvador");
        assertThat(result.orElseThrow().uf).isEqualTo("BA");
    }
}