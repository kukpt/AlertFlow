package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.model.AlarmEvent;

public interface AlarmEventSink {
    void emit(AlarmEvent event);
}
