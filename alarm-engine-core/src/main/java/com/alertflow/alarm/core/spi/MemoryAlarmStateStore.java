package com.alertflow.alarm.core.spi;

import com.alertflow.alarm.core.state.AlarmKey;
import com.alertflow.alarm.core.state.AlarmState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryAlarmStateStore implements AlarmStateStore {
    private final Map<AlarmKey, AlarmState> states = new ConcurrentHashMap<>();

    @Override
    public AlarmState get(AlarmKey key) {
        return states.getOrDefault(key, AlarmState.normal(key));
    }

    @Override
    public void save(AlarmState state) {
        states.put(state.key(), state);
    }

    @Override
    public Collection<AlarmState> findAll() {
        return states.values();
    }
}
