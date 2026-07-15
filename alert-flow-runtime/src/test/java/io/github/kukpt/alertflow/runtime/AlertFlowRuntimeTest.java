package io.github.kukpt.alertflow.runtime;

import io.github.kukpt.alertflow.core.model.*;
import io.github.kukpt.alertflow.core.spi.CollectingAlarmEventSink;
import io.github.kukpt.alertflow.core.spi.MemoryRuleProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertFlowRuntimeTest {
    @Test
    void lifecycleAndUpdatedFilteringAreApplied() {
        AlarmRule rule = new AlarmRule("R1", "threshold", null, "sensor", "temperature", RuleType.THRESHOLD,
                Duration.ZERO, Operator.GT, 80, "HIGH", true, null, DeltaDirection.ABS);
        CollectingAlarmEventSink sink = new CollectingAlarmEventSink();
        AlertFlowRuntime runtime = AlertFlowRuntime.builder()
                .ruleProvider(new MemoryRuleProvider(Collections.singletonList(rule))).eventSink(sink)
                .options(new AlertFlowOptions().setOfflineScanInterval(Duration.ZERO).setEmitUpdated(false)).build();
        assertThrows(IllegalStateException.class, () -> runtime.handle(data(81)));
        runtime.start();
        runtime.handle(data(81));
        runtime.handle(data(82));
        assertEquals(1, sink.events().size());
        assertEquals(AlarmEventType.TRIGGERED, sink.events().get(0).eventType());
        runtime.close();
        assertThrows(IllegalStateException.class, () -> runtime.handle(data(83)));
    }

    private DeviceData data(double value) {
        return new DeviceData("D1", "sensor", "temperature", value, "C", Instant.parse("2026-07-09T10:00:00Z"), null);
    }
}
