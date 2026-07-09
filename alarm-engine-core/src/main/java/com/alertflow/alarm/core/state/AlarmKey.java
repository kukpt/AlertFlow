package com.alertflow.alarm.core.state;

import com.alertflow.alarm.core.model.AlarmRule;
import com.alertflow.alarm.core.model.DeviceData;

import java.util.Objects;

public final class AlarmKey {
    private final String deviceId;
    private final String metric;
    private final String ruleId;

    public AlarmKey(String deviceId, String metric, String ruleId) {
        this.deviceId = deviceId;
        this.metric = metric;
        this.ruleId = ruleId;
    }

    public static AlarmKey of(DeviceData data, AlarmRule rule) {
        return new AlarmKey(data.deviceId(), data.metric(), rule.ruleId());
    }

    public static AlarmKey of(String deviceId, String metric, String ruleId) {
        return new AlarmKey(deviceId, metric, ruleId);
    }

    public String deviceId() { return deviceId; }
    public String metric() { return metric; }
    public String ruleId() { return ruleId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AlarmKey)) {
            return false;
        }
        AlarmKey alarmKey = (AlarmKey) other;
        return Objects.equals(deviceId, alarmKey.deviceId)
                && Objects.equals(metric, alarmKey.metric)
                && Objects.equals(ruleId, alarmKey.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, metric, ruleId);
    }
}
