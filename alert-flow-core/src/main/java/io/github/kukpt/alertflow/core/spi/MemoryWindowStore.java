package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.window.DataPoint;
import io.github.kukpt.alertflow.core.window.WindowBuffer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-process implementation intended for local development and single-node use. */
public final class MemoryWindowStore implements WindowStore {
    private final Map<AlarmKey, WindowBuffer> buffers = new ConcurrentHashMap<>();

    @Override
    public List<DataPoint> appendAndGet(AlarmKey key, DataPoint point, Duration windowSize) {
        return buffers.computeIfAbsent(key, ignored -> new WindowBuffer())
                .add(point.value(), point.reportTime(), windowSize);
    }
}
