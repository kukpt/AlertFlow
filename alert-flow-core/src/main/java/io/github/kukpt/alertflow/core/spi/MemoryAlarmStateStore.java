package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.state.AlarmState;

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
    public boolean compareAndSet(AlarmKey key, long expectedVersion, AlarmState next) {
        final boolean[] updated = {false};
        states.compute(key, (ignored, current) -> {
            AlarmState actual = current == null ? AlarmState.normal(key) : current;
            if (actual.version() != expectedVersion) {
                return current;
            }
            updated[0] = true;
            return next;
        });
        return updated[0];
    }

    @Override
    public Collection<AlarmState> findAll() {
        return states.values();
    }
}
