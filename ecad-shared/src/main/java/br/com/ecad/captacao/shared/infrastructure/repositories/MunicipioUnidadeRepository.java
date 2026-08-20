package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import br.com.ecad.captacao.shared.domain.entities.MunicipioUnidade;

public interface MunicipioUnidadeRepository {
    Optional<MunicipioUnidade> buscarPorUfMunicipio(String uf, String municipio) throws IOException;

    MunicipioUnidade criar(MunicipioUnidade municipioUnidade) throws IOException;

    List<MunicipioUnidade> listarPorUf(String uf) throws IOException;

    /**
     * Lista os municipios (com UF) que possuem mapeamento para Unidade ECAD.
     * Util para iterar por partition key do container de eventos sem precisar de cross-partition.
     */
    List<MunicipioUnidade> listarMunicipios() throws IOException;
}
