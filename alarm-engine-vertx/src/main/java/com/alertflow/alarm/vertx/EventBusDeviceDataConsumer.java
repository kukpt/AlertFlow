package com.alertflow.alarm.vertx;

import com.alertflow.alarm.core.AlarmEngine;
import com.alertflow.alarm.core.model.DeviceData;
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
    private final AlarmEngine alarmEngine;

    public EventBusDeviceDataConsumer(Vertx vertx, AlarmEngine alarmEngine) {
        this.vertx = vertx;
        this.alarmEngine = alarmEngine;
    }

    public MessageConsumer<JsonObject> start() {
        return vertx.eventBus().consumer(EventBusAddresses.DEVICE_DATA_REPORT, message -> {
            DeviceData data = fromJson(message.body());
            alarmEngine.handle(data);
        });
    }

    public static DeviceData fromJson(JsonObject json) {
        return new DeviceData(
                json.getString("deviceId"),
                json.getString("deviceType"),
                json.getString("metric"),
                json.getDouble("value"),
                json.getString("unit"),
                parseInstant(json.getString("reportTime")),
                tags(json.getJsonObject("tags"))
        );
    }

    private static Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        }
    }

    private static Map<String, String> tags(JsonObject tags) {
        if (tags == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (String fieldName : tags.fieldNames()) {
            result.put(fieldName, tags.getString(fieldName));
        }
        return result;
    }
}
