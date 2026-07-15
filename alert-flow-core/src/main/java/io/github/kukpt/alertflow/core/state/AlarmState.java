package io.github.kukpt.alertflow.core.state;

import io.github.kukpt.alertflow.core.model.AlarmStatus;

import java.time.Instant;

public final class AlarmState {
    private final AlarmKey key;
    private final AlarmStatus status;
    private final String eventId;
    private final Instant firstTriggerTime;
    private final Instant lastTriggerTime;
    private final double lastWindowValue;
    private final long version;

    public AlarmState(AlarmKey key, AlarmStatus status, String eventId, Instant firstTriggerTime, Instant lastTriggerTime, double lastWindowValue) {
        this(key, status, eventId, firstTriggerTime, lastTriggerTime, lastWindowValue, 0L);
    }

    public AlarmState(AlarmKey key, AlarmStatus status, String eventId, Instant firstTriggerTime, Instant lastTriggerTime, double lastWindowValue, long version) {
        this.key = key;
        this.status = status;
        this.eventId = eventId;
        this.firstTriggerTime = firstTriggerTime;
        this.lastTriggerTime = lastTriggerTime;
        this.lastWindowValue = lastWindowValue;
        this.version = version;
    }

    public static AlarmState normal(AlarmKey key) {
        return new AlarmState(key, AlarmStatus.NORMAL, null, null, null, Double.NaN);
    }

    public AlarmState alarming(String eventId, Instant firstTriggerTime, Instant lastTriggerTime, double lastWindowValue) {
        return new AlarmState(key, AlarmStatus.ALARMING, eventId, firstTriggerTime, lastTriggerTime, lastWindowValue, version + 1);
    }

    public AlarmState recovered(double lastWindowValue) {
        return new AlarmState(key, AlarmStatus.RECOVERED, eventId, firstTriggerTime, lastTriggerTime, lastWindowValue, version + 1);
    }

    public AlarmKey key() { return key; }
    public AlarmStatus status() { return status; }
    public String eventId() { return eventId; }
    public Instant firstTriggerTime() { return firstTriggerTime; }
    public Instant lastTriggerTime() { return lastTriggerTime; }
    public double lastWindowValue() { return lastWindowValue; }
    public long version() { return version; }
}
