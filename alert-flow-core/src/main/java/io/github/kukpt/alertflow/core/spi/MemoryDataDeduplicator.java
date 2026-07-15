package io.github.kukpt.alertflow.core.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryDataDeduplicator implements DataDeduplicator {
    private final Map<String, Instant> expirations = new ConcurrentHashMap<>();

    @Override
    public boolean markIfNew(String dataId, Duration retention) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(retention);
        final boolean[] added = {false};
        expirations.compute(dataId, (key, current) -> {
            if (current == null || current.isBefore(now)) {
                added[0] = true;
                return expiresAt;
            }
            return current;
        });
        return added[0];
    }
}
