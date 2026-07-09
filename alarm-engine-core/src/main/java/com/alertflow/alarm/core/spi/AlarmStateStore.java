package com.alertflow.alarm.core.spi;

import com.alertflow.alarm.core.state.AlarmKey;
import com.alertflow.alarm.core.state.AlarmState;

import java.util.Collection;

public interface AlarmStateStore {
    AlarmState get(AlarmKey key);

    void save(AlarmState state);

    Collection<AlarmState> findAll();
}
