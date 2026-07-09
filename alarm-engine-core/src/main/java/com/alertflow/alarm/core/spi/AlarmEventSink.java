package com.alertflow.alarm.core.spi;

import com.alertflow.alarm.core.model.AlarmEvent;

public interface AlarmEventSink {
    void emit(AlarmEvent event);
}
