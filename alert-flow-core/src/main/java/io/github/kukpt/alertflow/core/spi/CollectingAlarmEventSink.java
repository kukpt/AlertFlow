package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.model.AlarmEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CollectingAlarmEventSink implements AlarmEventSink {
    private final List<AlarmEvent> events = new ArrayList<>();

    @Override
    public synchronized void emit(AlarmEvent event) {
        events.add(event);
    }

    public synchronized List<AlarmEvent> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
}
