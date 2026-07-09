package com.alertflow.alarm.core.spi;

import com.alertflow.alarm.core.model.DeviceData;
import com.alertflow.alarm.core.model.AlarmRule;

import java.util.List;

public interface RuleProvider {
    List<AlarmRule> findRules(DeviceData data);

    List<AlarmRule> findOfflineRules();
}
