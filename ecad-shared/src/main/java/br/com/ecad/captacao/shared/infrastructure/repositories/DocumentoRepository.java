package br.com.ecad.captacao.shared.infrastructure.repositories;

import java.io.IOException;

import br.com.ecad.captacao.shared.domain.entities.Documento;

public interface DocumentoRepository {
    boolean urlJaFoiProcessada(String url) throws IOException;

    boolean arquivoJaFoiProcessado(String hashConteudo) throws IOException;

    void salvar(Documento documento) throws IOException;

    /**
     * Upsert idempotente. Usado quando o mesmo documento pode chegar por mais de um caminho
     * (ex.: scraper persiste apos publicar; processing-engine recebe o evento e tambem precisa garantir o registro).
     */
    void atualizar(Documento documento) throws IOException;
}
