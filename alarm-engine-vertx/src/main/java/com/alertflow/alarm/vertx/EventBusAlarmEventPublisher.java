package com.alertflow.alarm.vertx;

import com.alertflow.alarm.core.model.AlarmEvent;
import com.alertflow.alarm.core.model.AlarmEventType;
import com.alertflow.alarm.core.spi.AlarmEventSink;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class EventBusAlarmEventPublisher implements AlarmEventSink {
    private final Vertx vertx;

    public EventBusAlarmEventPublisher(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void emit(AlarmEvent event) {
        vertx.eventBus().publish(address(event.eventType()), toJson(event));
    }

    private String address(AlarmEventType eventType) {
        switch (eventType) {
            case TRIGGERED:
                return EventBusAddresses.ALARM_TRIGGERED;
            case UPDATED:
                return EventBusAddresses.ALARM_UPDATED;
            case RECOVERED:
                return EventBusAddresses.ALARM_RECOVERED;
            default:
                throw new IllegalStateException("Unsupported alarm event type: " + eventType);
        }
    }

    public static JsonObject toJson(AlarmEvent event) {
        return new JsonObject()
                .put("eventId", event.eventId())
                .put("ruleId", event.ruleId())
                .put("ruleName", event.ruleName())
                .put("deviceId", event.deviceId())
                .put("deviceType", event.deviceType())
                .put("metric", event.metric())
                .put("level", event.level())
                .put("eventType", event.eventType().name())
                .put("triggerValue", event.triggerValue())
                .put("windowValue", event.windowValue())
                .put("message", event.message())
                .put("firstTriggerTime", event.firstTriggerTime() == null ? null : event.firstTriggerTime().toString())
                .put("lastTriggerTime", event.lastTriggerTime() == null ? null : event.lastTriggerTime().toString())
                .put("recoverTime", event.recoverTime() == null ? null : event.recoverTime().toString());
    }
}
