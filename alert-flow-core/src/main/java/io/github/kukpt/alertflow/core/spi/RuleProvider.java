package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.core.model.AlarmRule;

import java.util.List;

public interface RuleProvider {
    List<AlarmRule> findRules(DeviceData data);

    List<AlarmRule> findOfflineRules();
}
