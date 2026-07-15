package io.github.kukpt.alertflow.runtime;

import java.time.Duration;
import java.time.ZoneId;

public final class AlertFlowOptions {
    private Duration offlineScanInterval = Duration.ofSeconds(30);
    private Duration deduplicationRetention = Duration.ofHours(24);
    private boolean emitUpdated = true;
    private Duration updatedMinInterval = Duration.ZERO;
    private ZoneId defaultZoneId = ZoneId.of("UTC");

    public Duration offlineScanInterval() { return offlineScanInterval; }
    public Duration deduplicationRetention() { return deduplicationRetention; }
    public boolean emitUpdated() { return emitUpdated; }
    public Duration updatedMinInterval() { return updatedMinInterval; }
    public ZoneId defaultZoneId() { return defaultZoneId; }

    public AlertFlowOptions setOfflineScanInterval(Duration value) { offlineScanInterval = nonNegative(value, "offlineScanInterval"); return this; }
    public AlertFlowOptions setDeduplicationRetention(Duration value) { deduplicationRetention = positive(value, "deduplicationRetention"); return this; }
    public AlertFlowOptions setEmitUpdated(boolean value) { emitUpdated = value; return this; }
    public AlertFlowOptions setUpdatedMinInterval(Duration value) { updatedMinInterval = nonNegative(value, "updatedMinInterval"); return this; }
    public AlertFlowOptions setDefaultZoneId(ZoneId value) { defaultZoneId = java.util.Objects.requireNonNull(value, "defaultZoneId"); return this; }

    private static Duration nonNegative(Duration value, String name) {
        java.util.Objects.requireNonNull(value, name);
        if (value.isNegative()) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static Duration positive(Duration value, String name) {
        nonNegative(value, name);
        if (value.isZero()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
