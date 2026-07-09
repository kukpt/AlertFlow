package com.alertflow.alarm.core;

import com.alertflow.alarm.core.model.AlarmEvent;
import com.alertflow.alarm.core.model.AlarmEventType;
import com.alertflow.alarm.core.model.AlarmRule;
import com.alertflow.alarm.core.model.AlarmStatus;
import com.alertflow.alarm.core.model.DeviceData;
import com.alertflow.alarm.core.rule.EvaluationResult;
import com.alertflow.alarm.core.rule.RuleEvaluator;
import com.alertflow.alarm.core.spi.AlarmEventSink;
import com.alertflow.alarm.core.spi.AlarmStateStore;
import com.alertflow.alarm.core.spi.RuleProvider;
import com.alertflow.alarm.core.state.AlarmKey;
import com.alertflow.alarm.core.state.AlarmState;
import com.alertflow.alarm.core.window.DataPoint;
import com.alertflow.alarm.core.window.WindowRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlarmEngine {
    private final RuleProvider ruleProvider;
    private final AlarmStateStore stateStore;
    private final AlarmEventSink eventSink;
    private final RuleEvaluator ruleEvaluator;
    private final WindowRegistry windowRegistry;
    private final Map<LastDataKey, DeviceData> lastData = new ConcurrentHashMap<>();

    public AlarmEngine(RuleProvider ruleProvider, AlarmStateStore stateStore, AlarmEventSink eventSink) {
        this(ruleProvider, stateStore, eventSink, new RuleEvaluator(), new WindowRegistry());
    }

    public AlarmEngine(
            RuleProvider ruleProvider,
            AlarmStateStore stateStore,
            AlarmEventSink eventSink,
            RuleEvaluator ruleEvaluator,
            WindowRegistry windowRegistry
    ) {
        this.ruleProvider = ruleProvider;
        this.stateStore = stateStore;
        this.eventSink = eventSink;
        this.ruleEvaluator = ruleEvaluator;
        this.windowRegistry = windowRegistry;
    }

    public List<AlarmEvent> handle(DeviceData data) {
        lastData.put(LastDataKey.of(data), data);
        List<AlarmEvent> events = new ArrayList<>();
        for (AlarmRule rule : ruleProvider.findRules(data)) {
            AlarmKey key = AlarmKey.of(data, rule);
            List<DataPoint> window = windowRegistry.get(key).add(data.value(), data.reportTime(), rule.windowSize());
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
            for (DeviceData data : lastData.values()) {
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
            AlarmState state = stateStore.get(key);
            if (state.status() != AlarmStatus.ALARMING) {
                continue;
            }
            AlarmEvent event = buildEvent(rule, data, state, AlarmEventType.RECOVERED, data.value(), data.reportTime());
            stateStore.save(state.recovered(data.value()));
            events.add(event);
        }
    }

    private java.util.Optional<AlarmEvent> toEvent(AlarmRule rule, DeviceData data, AlarmKey key, EvaluationResult result) {
        AlarmState state = stateStore.get(key);
        if (result.triggered()) {
            if (state.status() == AlarmStatus.ALARMING) {
                AlarmState updatedState = state.alarming(
                        state.eventId(),
                        state.firstTriggerTime(),
                        data.reportTime(),
                        result.windowValue()
                );
                stateStore.save(updatedState);
                return java.util.Optional.of(buildEvent(rule, data, updatedState, AlarmEventType.UPDATED, result.windowValue(), null));
            }
            String eventId = UUID.randomUUID().toString();
            AlarmState triggeredState = state.alarming(eventId, data.reportTime(), data.reportTime(), result.windowValue());
            stateStore.save(triggeredState);
            return java.util.Optional.of(buildEvent(rule, data, triggeredState, AlarmEventType.TRIGGERED, result.windowValue(), null));
        }
        if (state.status() == AlarmStatus.ALARMING && ruleEvaluator.isRecovered(rule, result)) {
            AlarmState recoveredState = state.recovered(result.windowValue());
            stateStore.save(recoveredState);
            return java.util.Optional.of(buildEvent(rule, data, state, AlarmEventType.RECOVERED, result.windowValue(), data.reportTime()));
        }
        if (state.status() == AlarmStatus.NORMAL) {
            stateStore.save(AlarmState.normal(key));
        }
        return java.util.Optional.empty();
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

    private static final class LastDataKey {
        private final String deviceId;
        private final String metric;

        private LastDataKey(String deviceId, String metric) {
            this.deviceId = deviceId;
            this.metric = metric;
        }

        static LastDataKey of(DeviceData data) {
            return new LastDataKey(data.deviceId(), data.metric());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LastDataKey)) {
                return false;
            }
            LastDataKey that = (LastDataKey) other;
            return java.util.Objects.equals(deviceId, that.deviceId)
                    && java.util.Objects.equals(metric, that.metric);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(deviceId, metric);
        }
    }
}
