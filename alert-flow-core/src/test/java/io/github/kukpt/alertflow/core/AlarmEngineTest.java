package io.github.kukpt.alertflow.core;

import io.github.kukpt.alertflow.core.model.AlarmEventType;
import io.github.kukpt.alertflow.core.model.AlarmRule;
import io.github.kukpt.alertflow.core.model.DeltaDirection;
import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.core.model.Operator;
import io.github.kukpt.alertflow.core.model.RuleType;
import io.github.kukpt.alertflow.core.spi.CollectingAlarmEventSink;
import io.github.kukpt.alertflow.core.spi.MemoryAlarmStateStore;
import io.github.kukpt.alertflow.core.spi.MemoryWindowStore;
import io.github.kukpt.alertflow.core.spi.MemoryDataDeduplicator;
import io.github.kukpt.alertflow.core.spi.MemoryLastSeenStore;
import io.github.kukpt.alertflow.core.spi.MemoryRuleProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Test
    void sharedWindowStorePreservesWindowWhenEngineIsRecreated() {
        AlarmRule rule = rule("R003", RuleType.DELTA, Duration.ofMinutes(5), Operator.GTE, 10.0);
        MemoryWindowStore windowStore = new MemoryWindowStore();
        MemoryAlarmStateStore stateStore = new MemoryAlarmStateStore();
        MemoryRuleProvider rules = new MemoryRuleProvider(Collections.singletonList(rule));

        AlarmEngine firstEngine = new AlarmEngine(rules, stateStore, new CollectingAlarmEventSink(), new io.github.kukpt.alertflow.core.rule.RuleEvaluator(), windowStore);
        assertEquals(0, firstEngine.handle(data(120.0, "2026-07-09T10:00:00Z")).size());

        AlarmEngine restartedEngine = new AlarmEngine(rules, stateStore, new CollectingAlarmEventSink(), new io.github.kukpt.alertflow.core.rule.RuleEvaluator(), windowStore);
        assertEquals(AlarmEventType.TRIGGERED, restartedEngine.handle(data(131.0, "2026-07-09T10:03:00Z")).get(0).eventType());
    }

    @Test
    void sharedLastSeenStorePreservesOfflineDetectionWhenEngineIsRecreated() {
        AlarmRule rule = rule("OFFLINE", RuleType.OFFLINE, Duration.ofMinutes(5), Operator.GTE, 0.0);
        MemoryLastSeenStore lastSeen = new MemoryLastSeenStore();
        MemoryAlarmStateStore states = new MemoryAlarmStateStore();
        MemoryRuleProvider rules = new MemoryRuleProvider(Collections.singletonList(rule));
        new AlarmEngine(rules, states, new CollectingAlarmEventSink(), new io.github.kukpt.alertflow.core.rule.RuleEvaluator(),
                new MemoryWindowStore(), lastSeen, null, Duration.ofHours(1)).handle(data(1, "2026-07-09T10:00:00Z"));

        AlarmEngine restarted = new AlarmEngine(rules, states, new CollectingAlarmEventSink(), new io.github.kukpt.alertflow.core.rule.RuleEvaluator(),
                new MemoryWindowStore(), lastSeen, null, Duration.ofHours(1));
        assertEquals(AlarmEventType.TRIGGERED, restarted.checkOffline(Instant.parse("2026-07-09T10:06:00Z")).get(0).eventType());
    }

    @Test
    void duplicateDataIdIsIgnored() {
        AlarmRule rule = rule("R004", RuleType.THRESHOLD, Duration.ZERO, Operator.GT, 80.0);
        AlarmEngine engine = new AlarmEngine(new MemoryRuleProvider(Collections.singletonList(rule)), new MemoryAlarmStateStore(),
                new CollectingAlarmEventSink(), new io.github.kukpt.alertflow.core.rule.RuleEvaluator(), new MemoryWindowStore(),
                new MemoryLastSeenStore(), new MemoryDataDeduplicator(), Duration.ofHours(1));
        DeviceData value = new DeviceData("DATA-1", "D001", "sensor", "temperature", 81, "C", Instant.parse("2026-07-09T10:00:00Z"), null);
        assertEquals(1, engine.handle(value).size());
        assertEquals(0, engine.handle(value).size());
    }

    @Test
    void olderOutOfOrderDataIsRejectedBeforeItCanChangeAlarmState() {
        AlarmRule rule = rule("R006", RuleType.THRESHOLD, Duration.ZERO, Operator.GT, 80.0);
        AlarmEngine engine = new AlarmEngine(new MemoryRuleProvider(Collections.singletonList(rule)), new MemoryAlarmStateStore(),
                new CollectingAlarmEventSink());
        assertEquals(AlarmEventType.TRIGGERED, engine.handle(data(81, "2026-07-09T10:02:00Z")).get(0).eventType());
        assertEquals(0, engine.handle(data(70, "2026-07-09T10:01:00Z")).size());
        assertEquals(AlarmEventType.UPDATED, engine.handle(data(82, "2026-07-09T10:03:00Z")).get(0).eventType());
    }

    @Test
    void concurrentEvaluationProducesOnlyOneTriggeredEvent() throws Exception {
        AlarmRule rule = rule("R005", RuleType.THRESHOLD, Duration.ZERO, Operator.GT, 80.0);
        CollectingAlarmEventSink sink = new CollectingAlarmEventSink();
        AlarmEngine engine = new AlarmEngine(new MemoryRuleProvider(Collections.singletonList(rule)), new MemoryAlarmStateStore(), sink);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 8; i++) {
            executor.submit(() -> { ready.countDown(); go.await(); engine.handle(data(81, "2026-07-09T10:00:00Z")); return null; });
        }
        ready.await();
        go.countDown();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, sink.events().stream().filter(e -> e.eventType() == AlarmEventType.TRIGGERED).count());
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
