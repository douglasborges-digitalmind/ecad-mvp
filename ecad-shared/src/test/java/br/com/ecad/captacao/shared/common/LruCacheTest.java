package br.com.ecad.captacao.shared.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LruCacheTest {

    @Test
    void deveArmazenarERecuperarValor() {
        var cache = new LruCache<String, String>(10);
        cache.put("chave", "valor");

        assertEquals(Optional.of("valor"), cache.get("chave"));
        assertEquals(1, cache.size());
    }

    @Test
    void deveRetornarVazioParaChaveInexistente() {
        var cache = new LruCache<String, String>(10);
        assertEquals(Optional.empty(), cache.get("nao-existe"));
    }

    @Test
    void naoDeveAceitarChaveNulaEmGet() {
        var cache = new LruCache<String, String>(10);
        assertEquals(Optional.empty(), cache.get(null));
    }

    @Test
    void deveLancarExcecaoParaChaveNulaEmPut() {
        var cache = new LruCache<String, String>(10);
        try {
            cache.put(null, "valor");
            fail("Esperava IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    void deveLancarExcecaoParaMaxSizeInvalido() {
        try {
            new LruCache<String, String>(0);
            fail("Esperava IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertNotNull(ex.getMessage());
        }
        try {
            new LruCache<String, String>(-1);
            fail("Esperava IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    void deveAplicarLruVerdadeiroNaEviction() {
        var cache = new LruCache<Integer, String>(3);
        cache.put(1, "um");
        cache.put(2, "dois");
        cache.put(3, "tres");
        // Acessar 1 e 2 os promove como MRU; a entrada menos usada passa a ser 3.
        cache.get(1);
        cache.get(2);
        // Inserir 4 deve evictar 3 (LRU), NAO 1.
        cache.put(4, "quatro");

        assertEquals(Optional.of("um"), cache.get(1));
        assertEquals(Optional.of("dois"), cache.get(2));
        assertEquals(Optional.empty(), cache.get(3), "Entrada 3 deveria ter sido evictada (LRU verdadeiro)");
        assertEquals(Optional.of("quatro"), cache.get(4));
        assertEquals(3, cache.size());
    }

    @Test
    void deveAplicarLruNaInsercaoAteLimite() {
        var cache = new LruCache<Integer, String>(2);
        cache.put(1, "um");
        cache.put(2, "dois");
        // Inserir 3 evicts 1 (primeira entrada, sem ter sido acessada).
        cache.put(3, "tres");

        assertEquals(Optional.empty(), cache.get(1));
        assertEquals(Optional.of("dois"), cache.get(2));
        assertEquals(Optional.of("tres"), cache.get(3));
    }

    @Test
    void deveAtualizarValorEmPutExistente() {
        var cache = new LruCache<String, String>(10);
        cache.put("chave", "v1");
        cache.put("chave", "v2");

        assertEquals(Optional.of("v2"), cache.get("chave"));
        assertEquals(1, cache.size());
    }

    @Test
    void deveRemoverEntrada() {
        var cache = new LruCache<String, String>(10);
        cache.put("chave", "valor");

        assertTrue(cache.remove("chave"));
        assertEquals(Optional.empty(), cache.get("chave"));
        assertEquals(0, cache.size());
    }

    @Test
    void removeDeveRetornarFalseParaChaveAusente() {
        var cache = new LruCache<String, String>(10);
        assertFalse(cache.remove("nao-existe"));
    }

    @Test
    void clearDeveRemoverTodasAsEntradas() {
        var cache = new LruCache<Integer, String>(10);
        cache.put(1, "um");
        cache.put(2, "dois");
        cache.put(3, "tres");

        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(Optional.empty(), cache.get(1));
    }

    @Test
    void constructorComTtlNaoLanca() {
        // Mantido para documentar a compatibilidade do construtor com TTL.
        // O TTL e ignorado na implementacao atual (LinkedHashMap access-order).
        var cache = new LruCache<String, String>(10, Duration.ZERO);
        cache.put("chave", "valor");
        assertEquals(Optional.of("valor"), cache.get("chave"));
    }

    @Test
    void containsKeyRemoveEntradaQuandoNaoExiste() {
        var cache = new LruCache<String, String>(10);
        assertFalse(cache.containsKey("nao-existe"));
        cache.put("existe", "valor");
        assertTrue(cache.containsKey("existe"));
    }

    @Test
    void deveSerThreadSafeSobConcorrencia() throws InterruptedException {
        var cache = new LruCache<Integer, Integer>(1000);
        var threads = 16;
        var iterations = 5_000;
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(threads);
        var failure = new AtomicReference<Throwable>();

        for (var t = 0; t < threads; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (var i = 0; i < iterations; i++) {
                        var key = (threadId * 17 + i) % 2000; // gera colisoes para exercitar eviction
                        if ((i & 1) == 0) {
                            cache.put(key, i);
                        } else {
                            cache.get(key);
                        }
                    }
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                } finally {
                    doneLatch.countDown();
                }
            }, "tester-" + t).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15, TimeUnit.SECONDS), "Threads nao terminaram em 15s");
        assertNull(failure.get(), "Nenhuma excecao esperada: " + failure.get());
        // Nao ha assercao forte sobre o tamanho final (depende de colisoes e evictions),
        // mas o teste nao deve ter lancado ConcurrentModificationException ou similar.
        assertTrue(cache.size() <= 1000);
    }
}
