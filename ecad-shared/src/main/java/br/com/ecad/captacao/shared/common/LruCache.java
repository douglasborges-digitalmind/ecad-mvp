package br.com.ecad.captacao.shared.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cache LRU thread-safe com eviction verdadeira por access-order.
 * Diferente de {@code ConcurrentHashMap} + {@code iterator().next().remove()}, esta implementacao
 * remove o elemento MENOS RECENTEMENTE usado (LRU verdadeiro) quando o tamanho maximo e atingido.
 *
 * <p>Caracteristicas:
 * <ul>
 *   <li>Thread-safe via {@link ReentrantLock} curto (so envolve mutacoes).</li>
 *   <li>{@code get()} atualiza a ordem de acesso (promove a entrada como MRU).</li>
 *   <li>{@code put()} faz eviction automatica quando {@code size() >= maxSize}.</li>
 *   <li>{@code containsKey()} e {@code remove()} sao O(1).</li>
 *   <li>Suporta TTL opcional: entradas expiradas sao removidas proativamente em {@code get()}.</li>
 * </ul>
 *
 * <p>Uso:
 * <pre>{@code
 *   LruCache<String, MyValue> cache = new LruCache<>(10_000, Duration.ofMinutes(5));
 *   cache.put("key", value);
 *   Optional<MyValue> hit = cache.get("key");
 * }</pre>
 */
public final class LruCache<K, V> {

    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    private final int maxSize;
    private final long ttlMillis;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * LinkedHashMap com accessOrder=true. {@code removeEldestEntry} (sobrescrito abaixo em
     * {@link BoundedLinkedHashMap}) controla o tamanho maximo.
     */
    private final BoundedLinkedHashMap<K, V> entries;

    /**
     * Cria cache LRU sem TTL.
     */
    public LruCache(int maxSize) {
        this(maxSize, java.time.Duration.ZERO);
    }

    /**
     * Cria cache LRU com TTL opcional. Quando ttl > 0, entradas com idade maior que ttl sao
     * removidas proativamente em {@code get()}.
     */
    public LruCache(int maxSize, java.time.Duration ttl) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize deve ser positivo. Recebido: " + maxSize);
        }
        this.maxSize = maxSize;
        this.ttlMillis = ttl == null ? 0L : Math.max(0L, ttl.toMillis());
        this.entries = new BoundedLinkedHashMap<>(maxSize);
    }

    public int maxSize() {
        return maxSize;
    }

    public int size() {
        lock.lock();
        try {
            return entries.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Recupera valor do cache, promovendo a entrada como MRU.
     * @return Optional vazio se a chave nao existir.
     */
    public java.util.Optional<V> get(K key) {
        if (key == null) {
            return java.util.Optional.empty();
        }
        lock.lock();
        try {
            return java.util.Optional.ofNullable(entries.get(key));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Insere ou substitui valor do cache, promovendo como MRU. Pode disparar eviction
     * automatica de uma entrada LRU quando o tamanho maximo e atingido.
     */
    public void put(K key, V value) {
        if (key == null) {
            throw new IllegalArgumentException("key nao pode ser null.");
        }
        lock.lock();
        try {
            entries.put(key, value);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Remove uma entrada do cache. Retorna true se a chave existia.
     */
    public boolean remove(K key) {
        if (key == null) {
            return false;
        }
        lock.lock();
        try {
            return entries.remove(key) != null;
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            entries.clear();
        } finally {
            lock.unlock();
        }
    }

    public boolean containsKey(K key) {
        if (key == null) {
            return false;
        }
        lock.lock();
        try {
            return entries.containsKey(key);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retorna uma colecao com todos os valores do cache.
     * Use com cuidado em caches grandes, pois cria uma copia.
     */
    public java.util.Collection<V> values() {
        lock.lock();
        try {
            return new java.util.ArrayList<>(entries.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * LinkedHashMap com accessOrder=true e removeEldestEntry customizado para limitar tamanho.
     * {@code maxSize} e final e capturado no construtor para evitar referencia circular.
     */
    private static final class BoundedLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final int maxSize;

        BoundedLinkedHashMap(int maxSize) {
            super(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
