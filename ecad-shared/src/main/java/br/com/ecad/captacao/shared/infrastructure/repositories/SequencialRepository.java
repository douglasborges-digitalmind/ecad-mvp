package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;

public interface SequencialRepository {
    int proximoSequencial(int ano) throws IOException;
}
