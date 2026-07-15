package io.github.kukpt.alertflow.vertx;

import io.github.kukpt.alertflow.core.model.DeviceData;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventBusDeviceDataConsumerTest {
    @Test
    void parsesConfiguredDefaultTimezoneAndDataId() {
        DeviceData data = EventBusDeviceDataConsumer.fromJson(new io.vertx.core.json.JsonObject()
                .put("dataId", "X1").put("deviceId", "D1").put("metric", "temperature")
                .put("value", 12).put("reportTime", "2026-07-09T10:00:00"), ZoneId.of("Asia/Shanghai"));
        assertEquals("X1", data.dataId());
        assertEquals("2026-07-09T02:00:00Z", data.reportTime().toString());
    }

    @Test
    void rejectsMissingRequiredFieldsAndNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> EventBusDeviceDataConsumer.fromJson(new io.vertx.core.json.JsonObject()));
        assertThrows(IllegalArgumentException.class, () -> EventBusDeviceDataConsumer.fromJson(new io.vertx.core.json.JsonObject()
                .put("deviceId", "D1").put("metric", "m").put("value", Double.NaN).put("reportTime", "2026-07-09T10:00:00Z")));
    }
}
