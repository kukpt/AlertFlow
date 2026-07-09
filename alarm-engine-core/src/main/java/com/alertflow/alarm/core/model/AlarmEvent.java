package com.alertflow.alarm.core.model;

import java.time.Instant;
import java.util.Objects;

public final class AlarmEvent {
    private final String eventId;
    private final String ruleId;
    private final String ruleName;
    private final String deviceId;
    private final String deviceType;
    private final String metric;
    private final String level;
    private final AlarmEventType eventType;
    private final double triggerValue;
    private final double windowValue;
    private final String message;
    private final Instant firstTriggerTime;
    private final Instant lastTriggerTime;
    private final Instant recoverTime;

    public AlarmEvent(String eventId, String ruleId, String ruleName, String deviceId, String deviceType, String metric, String level, AlarmEventType eventType, double triggerValue, double windowValue, String message, Instant firstTriggerTime, Instant lastTriggerTime, Instant recoverTime) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(eventType, "eventType");
        this.eventId = eventId;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.metric = metric;
        this.level = level;
        this.eventType = eventType;
        this.triggerValue = triggerValue;
        this.windowValue = windowValue;
        this.message = message;
        this.firstTriggerTime = firstTriggerTime;
        this.lastTriggerTime = lastTriggerTime;
        this.recoverTime = recoverTime;
    }

    public String eventId() { return eventId; }
    public String ruleId() { return ruleId; }
    public String ruleName() { return ruleName; }
    public String deviceId() { return deviceId; }
    public String deviceType() { return deviceType; }
    public String metric() { return metric; }
    public String level() { return level; }
    public AlarmEventType eventType() { return eventType; }
    public double triggerValue() { return triggerValue; }
    public double windowValue() { return windowValue; }
    public String message() { return message; }
    public Instant firstTriggerTime() { return firstTriggerTime; }
    public Instant lastTriggerTime() { return lastTriggerTime; }
    public Instant recoverTime() { return recoverTime; }
}
