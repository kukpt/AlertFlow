package io.github.kukpt.alertflow.core.window;

import io.github.kukpt.alertflow.core.spi.WindowStore;
import io.github.kukpt.alertflow.core.state.AlarmKey;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backward-compatible in-memory window store. Prefer {@link WindowStore} in
 * new integrations so a durable implementation can be supplied.
 */
public final class WindowRegistry implements WindowStore {
    private final Map<AlarmKey, WindowBuffer> buffers = new ConcurrentHashMap<>();

    public WindowBuffer get(AlarmKey key) {
        return buffers.computeIfAbsent(key, ignored -> new WindowBuffer());
    }

    @Override
    public List<DataPoint> appendAndGet(AlarmKey key, DataPoint point, Duration windowSize) {
        return get(key).add(point.value(), point.reportTime(), windowSize);
    }
}
