package io.github.kukpt.alertflow.runtime;

import io.github.kukpt.alertflow.core.model.AlarmEvent;
import io.github.kukpt.alertflow.core.model.AlarmEventType;
import io.github.kukpt.alertflow.core.spi.AlarmEventSink;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ThrottledAlarmEventSink implements AlarmEventSink {
    private final AlarmEventSink delegate;
    private final boolean emitUpdated;
    private final Duration minInterval;
    private final Map<String, Instant> lastUpdates = new ConcurrentHashMap<>();

    ThrottledAlarmEventSink(AlarmEventSink delegate, AlertFlowOptions options) {
        this.delegate = delegate;
        this.emitUpdated = options.emitUpdated();
        this.minInterval = options.updatedMinInterval();
    }

    @Override
    public void emit(AlarmEvent event) {
        if (event.eventType() != AlarmEventType.UPDATED) {
            if (event.eventType() == AlarmEventType.RECOVERED) lastUpdates.remove(event.eventId());
            delegate.emit(event);
            return;
        }
        if (!emitUpdated) return;
        Instant eventTime = event.lastTriggerTime() == null ? Instant.now() : event.lastTriggerTime();
        final boolean[] accepted = {false};
        lastUpdates.compute(event.eventId(), (key, previous) -> {
            if (previous == null || !eventTime.isBefore(previous.plus(minInterval))) {
                accepted[0] = true;
                return eventTime;
            }
            return previous;
        });
        if (accepted[0]) delegate.emit(event);
    }
}
