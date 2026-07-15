package io.github.kukpt.alertflow.core.spi;

import java.time.Duration;

/** Optional idempotency gate for inputs carrying a dataId. */
public interface DataDeduplicator {
    boolean markIfNew(String dataId, Duration retention);
}
