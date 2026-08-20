package br.com.ecad.captacao.shared.infrastructure.quarantine;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryEventFailureTracker implements EventFailureTracker {
    private final ConcurrentHashMap<String, EventFailureState> attempts = new ConcurrentHashMap<>();

    @Override
    public EventFailureState increment(String messageId) {
        return attempts.compute(messageId, (key, current) -> {
            var now = OffsetDateTime.now(ZoneOffset.UTC);
            return current == null
                ? new EventFailureState(1, now, now)
                : new EventFailureState(current.attemptCount() + 1, current.firstFailureUtc(), now);
        });
    }

    @Override
    public void clear(String messageId) {
        attempts.remove(messageId);
    }
}