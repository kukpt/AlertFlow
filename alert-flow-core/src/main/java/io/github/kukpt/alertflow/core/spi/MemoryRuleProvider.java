package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.model.AlarmRule;
import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.core.model.RuleType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class MemoryRuleProvider implements RuleProvider {
    private final CopyOnWriteArrayList<AlarmRule> rules = new CopyOnWriteArrayList<>();

    public MemoryRuleProvider(List<AlarmRule> initialRules) {
        if (initialRules != null) {
            rules.addAll(initialRules);
        }
    }

    public void addRule(AlarmRule rule) {
        rules.add(rule);
    }

    @Override
    public List<AlarmRule> findRules(DeviceData data) {
        return rules.stream()
                .filter(rule -> rule.ruleType() != RuleType.OFFLINE)
                .filter(rule -> rule.matches(data))
                .collect(Collectors.toList());
    }

    @Override
    public List<AlarmRule> findOfflineRules() {
        return rules.stream()
                .filter(AlarmRule::enabled)
                .filter(rule -> rule.ruleType() == RuleType.OFFLINE)
                .collect(Collectors.toList());
    }

    public List<AlarmRule> snapshot() {
        return new ArrayList<>(rules);
    }
}
