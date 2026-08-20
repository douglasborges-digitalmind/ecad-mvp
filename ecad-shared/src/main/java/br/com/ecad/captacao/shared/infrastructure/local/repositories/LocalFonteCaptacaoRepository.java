package br.com.ecad.captacao.shared.infrastructure.local.repositories;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import br.com.ecad.captacao.shared.domain.entities.FonteCaptacao;
import br.com.ecad.captacao.shared.infrastructure.local.LocalJsonFileStore;
import br.com.ecad.captacao.shared.infrastructure.repositories.FonteCaptacaoRepository;

public class LocalFonteCaptacaoRepository implements FonteCaptacaoRepository {
    private static final String COLLECTION_NAME = "fontes-captacao";

    private final LocalJsonFileStore store;
    public LocalFonteCaptacaoRepository(LocalJsonFileStore store) {
        this.store = store;
    }

    @Override
    public FonteCaptacao criar(FonteCaptacao fonte) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, FonteCaptacao.class, items -> {
            items.add(fonte);
            return fonte;
        });
    }

    @Override
    public Optional<FonteCaptacao> obterPorId(UUID id) throws IOException {
        return store.readCollection(COLLECTION_NAME, FonteCaptacao.class).stream()
            .filter(item -> item.id.equals(id))
            .findFirst();
    }

    @Override
    public List<FonteCaptacao> listar(String unidadeEcad, Boolean ativo) throws IOException {
        return store.readCollection(COLLECTION_NAME, FonteCaptacao.class).stream()
            .filter(item -> isBlank(unidadeEcad) || item.unidadeEcad != null && item.unidadeEcad.equalsIgnoreCase(unidadeEcad))
            .filter(item -> ativo == null || item.canaisScraping.stream().anyMatch(canal -> canal.ativo == ativo))
            .sorted(Comparator.comparing(item -> item.nome, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
    }

    @Override
    public FonteCaptacao atualizar(FonteCaptacao fonte) throws IOException {
        return store.mutateCollection(COLLECTION_NAME, FonteCaptacao.class, items -> {
            for (var index = 0; index < items.size(); index++) {
                if (items.get(index).id.equals(fonte.id)) {
                    items.set(index, fonte);
                    return fonte;
                }
            }

            throw new NoSuchElementException("Fonte " + fonte.id + " nao encontrada.");
        });
    }

    @Override
    public void remover(UUID id, String unidadeEcad) throws IOException {
        store.mutateCollection(COLLECTION_NAME, FonteCaptacao.class, items -> items.removeIf(item ->
            item.id.equals(id) && item.unidadeEcad != null && item.unidadeEcad.equalsIgnoreCase(unidadeEcad)));
    }

    @Override
    public List<FonteCaptacao> listarComScrapingsVencidos(OffsetDateTime dataReferencia) throws IOException {
        return store.readCollection(COLLECTION_NAME, FonteCaptacao.class).stream()
            .filter(fonte -> fonte.canaisScraping.stream().anyMatch(canal ->
                canal.ativo && canal.frequencia != null && canal.frequencia.proximaExecucao != null
                    && !canal.frequencia.proximaExecucao.isAfter(dataReferencia)))
            .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
