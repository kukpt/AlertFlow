package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.window.DataPoint;

import java.time.Duration;
import java.util.List;

/**
 * Stores the data points used to evaluate one rule's sliding window.
 * Implementations must make appending a point and removing expired points atomic.
 */
public interface WindowStore {
    List<DataPoint> appendAndGet(AlarmKey key, DataPoint point, Duration windowSize);
}
