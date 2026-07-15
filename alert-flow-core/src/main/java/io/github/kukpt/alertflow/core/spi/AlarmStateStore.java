package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.state.AlarmState;

import java.util.Collection;

public interface AlarmStateStore {
    AlarmState get(AlarmKey key);

    /** Saves state unconditionally. Intended for initialization and migration. */
    void save(AlarmState state);

    /** Atomically saves {@code next} only when the stored version equals {@code expectedVersion}. */
    boolean compareAndSet(AlarmKey key, long expectedVersion, AlarmState next);

    Collection<AlarmState> findAll();
}
