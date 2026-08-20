package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;

import br.com.ecad.captacao.shared.domain.entities.Documento;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.DocumentoRepository;

public class LocalDocumentoRepository implements DocumentoRepository {
    private static final String COLLECTION_NAME = "documentos";

    private final LocalJsonFileStore store;

    public LocalDocumentoRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public boolean urlJaFoiProcessada(String url) throws IOException {
        return store.readCollection(COLLECTION_NAME, Documento.class).stream()
            .anyMatch(item -> item.url() != null && item.url().equalsIgnoreCase(url));
    }

    @Override
    public boolean arquivoJaFoiProcessado(String hashConteudo) throws IOException {
        return store.readCollection(COLLECTION_NAME, Documento.class).stream()
            .anyMatch(item -> item.hashConteudo() != null && item.hashConteudo().equalsIgnoreCase(hashConteudo));
    }

    @Override
    public void salvar(Documento documento) throws IOException {
        store.mutateCollection(COLLECTION_NAME, Documento.class, items -> {
            items.add(documento);
            return true;
        });
    }

    @Override
    public void atualizar(Documento documento) throws IOException {
        store.mutateCollection(COLLECTION_NAME, Documento.class, items -> {
            items.removeIf(item -> item.id() != null && item.id().equals(documento.id()));
            items.add(documento);
            return true;
        });
    }
}
