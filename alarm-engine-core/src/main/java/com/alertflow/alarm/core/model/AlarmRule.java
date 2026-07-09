package com.alertflow.alarm.core.model;

import java.time.Duration;
import java.util.Objects;

public final class AlarmRule {
    private final String ruleId;
    private final String ruleName;
    private final String deviceId;
    private final String deviceType;
    private final String metric;
    private final RuleType ruleType;
    private final Duration windowSize;
    private final Operator operator;
    private final double threshold;
    private final String level;
    private final boolean enabled;
    private final RecoverRule recoverRule;
    private final DeltaDirection deltaDirection;

    public AlarmRule(String ruleId, String ruleName, String deviceId, String deviceType, String metric, RuleType ruleType, Duration windowSize, Operator operator, double threshold, String level, boolean enabled, RecoverRule recoverRule, DeltaDirection deltaDirection) {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(ruleName, "ruleName");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(ruleType, "ruleType");
        Objects.requireNonNull(operator, "operator");
        if (windowSize == null || windowSize.isNegative() || windowSize.isZero()) {
            windowSize = Duration.ZERO;
        }
        if (deltaDirection == null) {
            deltaDirection = DeltaDirection.ABS;
        }
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.metric = metric;
        this.ruleType = ruleType;
        this.windowSize = windowSize;
        this.operator = operator;
        this.threshold = threshold;
        this.level = level;
        this.enabled = enabled;
        this.recoverRule = recoverRule;
        this.deltaDirection = deltaDirection;
    }

    public boolean matches(DeviceData data) {
        if (!enabled) {
            return false;
        }
        if (deviceId != null && !deviceId.equals(data.deviceId())) {
            return false;
        }
        if (deviceType != null && !deviceType.equals(data.deviceType())) {
            return false;
        }
        return metric.equals(data.metric());
    }

    public String ruleId() { return ruleId; }
    public String ruleName() { return ruleName; }
    public String deviceId() { return deviceId; }
    public String deviceType() { return deviceType; }
    public String metric() { return metric; }
    public RuleType ruleType() { return ruleType; }
    public Duration windowSize() { return windowSize; }
    public Operator operator() { return operator; }
    public double threshold() { return threshold; }
    public String level() { return level; }
    public boolean enabled() { return enabled; }
    public RecoverRule recoverRule() { return recoverRule; }
    public DeltaDirection deltaDirection() { return deltaDirection; }
}
