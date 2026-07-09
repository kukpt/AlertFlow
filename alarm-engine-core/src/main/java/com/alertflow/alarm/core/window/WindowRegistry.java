package com.alertflow.alarm.core.window;

import com.alertflow.alarm.core.state.AlarmKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WindowRegistry {
    private final Map<AlarmKey, WindowBuffer> buffers = new ConcurrentHashMap<>();

    public WindowBuffer get(AlarmKey key) {
        return buffers.computeIfAbsent(key, ignored -> new WindowBuffer());
    }
}
