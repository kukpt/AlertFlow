package io.github.kukpt.alertflow.vertx;

import io.github.kukpt.alertflow.core.model.AlarmEvent;
import io.github.kukpt.alertflow.core.model.AlarmEventType;
import io.github.kukpt.alertflow.core.spi.AlarmEventSink;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class EventBusAlarmEventPublisher implements AlarmEventSink {
    private final Vertx vertx;
    private final VertxAlertFlowOptions options;

    public EventBusAlarmEventPublisher(Vertx vertx) {
        this(vertx, new VertxAlertFlowOptions());
    }

    public EventBusAlarmEventPublisher(Vertx vertx, VertxAlertFlowOptions options) {
        this.vertx = vertx;
        this.options = options;
    }

    @Override
    public void emit(AlarmEvent event) {
        vertx.eventBus().publish(address(event.eventType()), toJson(event));
    }

    private String address(AlarmEventType eventType) {
        switch (eventType) {
            case TRIGGERED:
                return options.triggeredAddress();
            case UPDATED:
                return options.updatedAddress();
            case RECOVERED:
                return options.recoveredAddress();
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
