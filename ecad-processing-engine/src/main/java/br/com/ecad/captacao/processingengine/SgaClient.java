package br.com.ecad.captacao.processingengine;

import br.com.ecad.captacao.shared.domain.enums.StatusSGA;
import br.com.ecad.captacao.shared.domain.exceptions.SgaException;

interface SgaClient {
    StatusSGA verificar(String titulo, String local, String dataInicio, String dataFim, String uf) throws SgaException;
}
