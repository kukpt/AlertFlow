package com.alertflow.alarm.core;

import com.alertflow.alarm.core.model.AlarmEventType;
import com.alertflow.alarm.core.model.AlarmRule;
import com.alertflow.alarm.core.model.DeltaDirection;
import com.alertflow.alarm.core.model.DeviceData;
import com.alertflow.alarm.core.model.Operator;
import com.alertflow.alarm.core.model.RuleType;
import com.alertflow.alarm.core.spi.CollectingAlarmEventSink;
import com.alertflow.alarm.core.spi.MemoryAlarmStateStore;
import com.alertflow.alarm.core.spi.MemoryRuleProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlarmEngineTest {
    @Test
    void thresholdRuleTriggersUpdatesAndRecoversWithoutCreatingDuplicateEvents() {
        AlarmRule rule = rule("R001", RuleType.THRESHOLD, Duration.ZERO, Operator.GT, 80.0);
        CollectingAlarmEventSink sink = new CollectingAlarmEventSink();
        AlarmEngine engine = new AlarmEngine(
                new MemoryRuleProvider(Collections.singletonList(rule)),
                new MemoryAlarmStateStore(),
                sink
        );

        assertEquals(AlarmEventType.TRIGGERED, engine.handle(data(81.0, "2026-07-09T10:00:00Z")).get(0).eventType());
        assertEquals(AlarmEventType.UPDATED, engine.handle(data(82.0, "2026-07-09T10:01:00Z")).get(0).eventType());
        assertEquals(AlarmEventType.RECOVERED, engine.handle(data(79.0, "2026-07-09T10:02:00Z")).get(0).eventType());
        assertEquals(3, sink.events().size());
    }

    @Test
    void deltaRuleUsesSlidingWindowStartAndCurrentValue() {
        AlarmRule rule = rule("R002", RuleType.DELTA, Duration.ofMinutes(5), Operator.GTE, 10.0);
        AlarmEngine engine = new AlarmEngine(
                new MemoryRuleProvider(Collections.singletonList(rule)),
                new MemoryAlarmStateStore(),
                new CollectingAlarmEventSink()
        );

        assertEquals(0, engine.handle(data(120.0, "2026-07-09T10:00:00Z")).size());
        assertEquals(AlarmEventType.TRIGGERED, engine.handle(data(131.0, "2026-07-09T10:03:00Z")).get(0).eventType());
        assertEquals(AlarmEventType.RECOVERED, engine.handle(data(122.0, "2026-07-09T10:07:00Z")).get(0).eventType());
    }

    private AlarmRule rule(String ruleId, RuleType ruleType, Duration windowSize, Operator operator, double threshold) {
        return new AlarmRule(
                ruleId,
                "测试规则",
                null,
                "sensor",
                "temperature",
                ruleType,
                windowSize,
                operator,
                threshold,
                "HIGH",
                true,
                null,
                DeltaDirection.ABS
        );
    }

    private DeviceData data(double value, String reportTime) {
        return new DeviceData(
                "D001",
                "sensor",
                "temperature",
                value,
                "C",
                Instant.parse(reportTime),
                null
        );
    }
}
