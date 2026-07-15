package io.github.kukpt.alertflow.core;

import io.github.kukpt.alertflow.core.model.AlarmEvent;
import io.github.kukpt.alertflow.core.model.AlarmEventType;
import io.github.kukpt.alertflow.core.model.AlarmRule;
import io.github.kukpt.alertflow.core.model.AlarmStatus;
import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.core.rule.EvaluationResult;
import io.github.kukpt.alertflow.core.rule.RuleEvaluator;
import io.github.kukpt.alertflow.core.spi.AlarmEventSink;
import io.github.kukpt.alertflow.core.spi.AlarmStateStore;
import io.github.kukpt.alertflow.core.spi.MemoryWindowStore;
import io.github.kukpt.alertflow.core.spi.MemoryLastSeenStore;
import io.github.kukpt.alertflow.core.spi.DataDeduplicator;
import io.github.kukpt.alertflow.core.spi.LastSeenStore;
import io.github.kukpt.alertflow.core.spi.RuleProvider;
import io.github.kukpt.alertflow.core.spi.WindowStore;
import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.state.AlarmState;
import io.github.kukpt.alertflow.core.window.DataPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AlarmEngine {
    private final RuleProvider ruleProvider;
    private final AlarmStateStore stateStore;
    private final AlarmEventSink eventSink;
    private final RuleEvaluator ruleEvaluator;
    private final WindowStore windowStore;
    private final LastSeenStore lastSeenStore;
    private final DataDeduplicator dataDeduplicator;
    private final Duration deduplicationRetention;

    public AlarmEngine(RuleProvider ruleProvider, AlarmStateStore stateStore, AlarmEventSink eventSink) {
        this(ruleProvider, stateStore, eventSink, new RuleEvaluator(), new MemoryWindowStore(), new MemoryLastSeenStore(), null, Duration.ofHours(24));
    }

    public AlarmEngine(
            RuleProvider ruleProvider,
            AlarmStateStore stateStore,
            AlarmEventSink eventSink,
            RuleEvaluator ruleEvaluator,
            WindowStore windowStore
    ) {
        this(ruleProvider, stateStore, eventSink, ruleEvaluator, windowStore, new MemoryLastSeenStore(), null, Duration.ofHours(24));
    }

    public AlarmEngine(
            RuleProvider ruleProvider,
            AlarmStateStore stateStore,
            AlarmEventSink eventSink,
            RuleEvaluator ruleEvaluator,
            WindowStore windowStore,
            LastSeenStore lastSeenStore,
            DataDeduplicator dataDeduplicator,
            Duration deduplicationRetention
    ) {
        this.ruleProvider = java.util.Objects.requireNonNull(ruleProvider, "ruleProvider");
        this.stateStore = java.util.Objects.requireNonNull(stateStore, "stateStore");
        this.eventSink = java.util.Objects.requireNonNull(eventSink, "eventSink");
        this.ruleEvaluator = java.util.Objects.requireNonNull(ruleEvaluator, "ruleEvaluator");
        this.windowStore = java.util.Objects.requireNonNull(windowStore, "windowStore");
        this.lastSeenStore = java.util.Objects.requireNonNull(lastSeenStore, "lastSeenStore");
        this.dataDeduplicator = dataDeduplicator;
        this.deduplicationRetention = deduplicationRetention == null ? Duration.ofHours(24) : deduplicationRetention;
    }

    public List<AlarmEvent> handle(DeviceData data) {
        if (data.dataId() != null && dataDeduplicator != null
                && !dataDeduplicator.markIfNew(data.dataId(), deduplicationRetention)) {
            return java.util.Collections.emptyList();
        }
        if (!lastSeenStore.saveLatest(data)) {
            return java.util.Collections.emptyList();
        }
        List<AlarmEvent> events = new ArrayList<>();
        for (AlarmRule rule : ruleProvider.findRules(data)) {
            AlarmKey key = AlarmKey.of(data, rule);
            List<DataPoint> window = windowStore.appendAndGet(
                    key,
                    new DataPoint(data.value(), data.reportTime()),
                    rule.windowSize()
            );
            EvaluationResult result = ruleEvaluator.evaluate(rule, data, window);
            toEvent(rule, data, key, result).ifPresent(events::add);
        }
        recoverOfflineRules(data, events);
        events.forEach(eventSink::emit);
        return events;
    }

    public List<AlarmEvent> checkOffline(Instant now) {
        List<AlarmEvent> events = new ArrayList<>();
        for (AlarmRule rule : ruleProvider.findOfflineRules()) {
            for (DeviceData data : lastSeenStore.findAll()) {
                if (!rule.matches(data)) {
                    continue;
                }
                Duration silence = Duration.between(data.reportTime(), now);
                if (!silence.minus(rule.windowSize()).isNegative()) {
                    AlarmKey key = AlarmKey.of(data, rule);
                    EvaluationResult result = new EvaluationResult(true, silence.getSeconds());
                    toEvent(rule, data, key, result).ifPresent(events::add);
                }
            }
        }
        events.forEach(eventSink::emit);
        return events;
    }

    private void recoverOfflineRules(DeviceData data, List<AlarmEvent> events) {
        for (AlarmRule rule : ruleProvider.findOfflineRules()) {
            if (!rule.matches(data)) {
                continue;
            }
            AlarmKey key = AlarmKey.of(data, rule);
            while (true) {
                AlarmState state = stateStore.get(key);
                if (state.status() != AlarmStatus.ALARMING) {
                    break;
                }
                AlarmState recovered = state.recovered(data.value());
                if (stateStore.compareAndSet(key, state.version(), recovered)) {
                    events.add(buildEvent(rule, data, state, AlarmEventType.RECOVERED, data.value(), data.reportTime()));
                    break;
                }
            }
        }
    }

    private java.util.Optional<AlarmEvent> toEvent(AlarmRule rule, DeviceData data, AlarmKey key, EvaluationResult result) {
        while (true) {
            AlarmState state = stateStore.get(key);
            AlarmState next;
            AlarmEventType eventType;
            if (result.triggered()) {
                if (state.status() == AlarmStatus.ALARMING) {
                    next = state.alarming(state.eventId(), state.firstTriggerTime(), data.reportTime(), result.windowValue());
                    eventType = AlarmEventType.UPDATED;
                } else {
                    next = state.alarming(UUID.randomUUID().toString(), data.reportTime(), data.reportTime(), result.windowValue());
                    eventType = AlarmEventType.TRIGGERED;
                }
            } else if (state.status() == AlarmStatus.ALARMING && ruleEvaluator.isRecovered(rule, result)) {
                next = state.recovered(result.windowValue());
                eventType = AlarmEventType.RECOVERED;
            } else {
                return java.util.Optional.empty();
            }
            if (stateStore.compareAndSet(key, state.version(), next)) {
                AlarmState eventState = eventType == AlarmEventType.RECOVERED ? state : next;
                Instant recoverTime = eventType == AlarmEventType.RECOVERED ? data.reportTime() : null;
                return java.util.Optional.of(buildEvent(rule, data, eventState, eventType, result.windowValue(), recoverTime));
            }
        }
    }

    private AlarmEvent buildEvent(
            AlarmRule rule,
            DeviceData data,
            AlarmState state,
            AlarmEventType eventType,
            double windowValue,
            Instant recoverTime
    ) {
        return new AlarmEvent(
                state.eventId(),
                rule.ruleId(),
                rule.ruleName(),
                data.deviceId(),
                data.deviceType(),
                data.metric(),
                rule.level(),
                eventType,
                data.value(),
                windowValue,
                message(rule, data, eventType, windowValue),
                state.firstTriggerTime(),
                state.lastTriggerTime(),
                recoverTime
        );
    }

    private String message(AlarmRule rule, DeviceData data, AlarmEventType eventType, double windowValue) {
        return String.format(
                "%s %s: device=%s metric=%s value=%.4f windowValue=%.4f rule=%s %s %.4f",
                rule.level(),
                eventType,
                data.deviceId(),
                data.metric(),
                data.value(),
                windowValue,
                rule.ruleType(),
                rule.operator().symbol(),
                rule.threshold()
        );
    }

}
