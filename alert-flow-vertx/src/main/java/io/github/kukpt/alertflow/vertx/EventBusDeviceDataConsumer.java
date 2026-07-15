package io.github.kukpt.alertflow.vertx;

import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.runtime.AlertFlowRuntime;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class EventBusDeviceDataConsumer {
    private final Vertx vertx;
    private final AlertFlowRuntime runtime;
    private final VertxAlertFlowOptions options;

    public EventBusDeviceDataConsumer(Vertx vertx, AlertFlowRuntime runtime, VertxAlertFlowOptions options) {
        this.vertx = vertx;
        this.runtime = runtime;
        this.options = options;
    }

    public MessageConsumer<JsonObject> start() {
        return vertx.eventBus().<JsonObject>consumer(options.inputAddress(), message -> {
            final DeviceData data;
            try {
                data = fromJson(message.body(), options.runtimeOptions().defaultZoneId());
            } catch (RuntimeException error) {
                vertx.eventBus().publish(options.invalidInputAddress(), invalidMessage(message.body(), error));
                if (message.replyAddress() != null) message.fail(400, error.getMessage());
                return;
            }
            vertx.executeBlocking(() -> {
                runtime.handle(data);
                return null;
            }, options.orderedWorkerExecution()).onFailure(error ->
                    vertx.eventBus().publish(options.invalidInputAddress(), invalidMessage(message.body(), error)));
        });
    }

    public static DeviceData fromJson(JsonObject json) {
        return fromJson(json, ZoneId.of("UTC"));
    }

    public static DeviceData fromJson(JsonObject json, ZoneId defaultZone) {
        if (json == null) throw new IllegalArgumentException("message body must be a JSON object");
        String deviceId = required(json, "deviceId");
        String metric = required(json, "metric");
        String reportTime = required(json, "reportTime");
        Number value = json.getNumber("value");
        if (value == null || !Double.isFinite(value.doubleValue())) throw new IllegalArgumentException("value must be a finite number");
        return new DeviceData(json.getString("dataId"), deviceId, json.getString("deviceType"), metric,
                value.doubleValue(), json.getString("unit"), parseInstant(reportTime, defaultZone), tags(json.getJsonObject("tags")));
    }

    private static String required(JsonObject json, String field) {
        String value = json.getString(field);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    private static Instant parseInstant(String text, ZoneId defaultZone) {
        try { return Instant.parse(text); }
        catch (DateTimeParseException ignored) {
            try { return LocalDateTime.parse(text).atZone(defaultZone).toInstant(); }
            catch (DateTimeParseException error) { throw new IllegalArgumentException("reportTime must be ISO-8601", error); }
        }
    }

    private static Map<String, String> tags(JsonObject tags) {
        if (tags == null) return Collections.emptyMap();
        Map<String, String> result = new HashMap<>();
        for (String fieldName : tags.fieldNames()) {
            Object value = tags.getValue(fieldName);
            if (value != null) result.put(fieldName, String.valueOf(value));
        }
        return result;
    }

    private static JsonObject invalidMessage(JsonObject body, Throwable error) {
        return new JsonObject().put("error", error.getMessage()).put("body", body).put("timestamp", Instant.now().toString());
    }
}
