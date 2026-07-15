package io.github.kukpt.alertflow.example;

import io.github.kukpt.alertflow.core.model.AlarmRule;
import io.github.kukpt.alertflow.core.model.DeltaDirection;
import io.github.kukpt.alertflow.core.model.Operator;
import io.github.kukpt.alertflow.core.model.RuleType;
import io.github.kukpt.alertflow.core.spi.MemoryRuleProvider;
import io.github.kukpt.alertflow.vertx.EventBusAddresses;
import io.github.kukpt.alertflow.vertx.VertxAlarmEngineStarter;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

public final class EventBusExample {
    private EventBusExample() {
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        AlarmRule rule = new AlarmRule(
                "R001",
                "5分钟位移变化预警",
                null,
                "displacement_sensor",
                "displacement",
                RuleType.DELTA,
                Duration.ofMinutes(5),
                Operator.GTE,
                10.0,
                "HIGH",
                true,
                null,
                DeltaDirection.ABS
        );
        new VertxAlarmEngineStarter(vertx, new MemoryRuleProvider(Collections.singletonList(rule))).start();

        vertx.eventBus().consumer(EventBusAddresses.ALARM_TRIGGERED, message -> {
            System.out.println("TRIGGERED " + message.body());
        });
        vertx.eventBus().consumer(EventBusAddresses.ALARM_UPDATED, message -> {
            System.out.println("UPDATED " + message.body());
        });
        vertx.eventBus().consumer(EventBusAddresses.ALARM_RECOVERED, message -> {
            System.out.println("RECOVERED " + message.body());
        });

        vertx.eventBus().publish(EventBusAddresses.DEVICE_DATA_REPORT, sampleData(120.5, "2026-07-09T10:00:00"));
        vertx.eventBus().publish(EventBusAddresses.DEVICE_DATA_REPORT, sampleData(132.0, "2026-07-09T10:03:00"));
    }

    private static JsonObject sampleData(double value, String reportTime) {
        return new JsonObject()
                .put("deviceId", "D001")
                .put("deviceType", "displacement_sensor")
                .put("metric", "displacement")
                .put("value", value)
                .put("unit", "mm")
                .put("reportTime", reportTime);
    }
}
