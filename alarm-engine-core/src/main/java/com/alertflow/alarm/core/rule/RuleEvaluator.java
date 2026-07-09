package com.alertflow.alarm.core.rule;

import com.alertflow.alarm.core.model.AlarmRule;
import com.alertflow.alarm.core.model.DeviceData;
import com.alertflow.alarm.core.model.RecoverRule;
import com.alertflow.alarm.core.window.DataPoint;

import java.time.Duration;
import java.util.List;

public final class RuleEvaluator {
    public EvaluationResult evaluate(AlarmRule rule, DeviceData data, List<DataPoint> window) {
        double windowValue = calculateWindowValue(rule, data, window);
        return new EvaluationResult(rule.operator().test(windowValue, rule.threshold()), windowValue);
    }

    public boolean isRecovered(AlarmRule rule, EvaluationResult result) {
        RecoverRule recoverRule = rule.recoverRule();
        if (recoverRule != null) {
            return recoverRule.operator().test(result.windowValue(), recoverRule.threshold());
        }
        return !result.triggered();
    }

    private double calculateWindowValue(AlarmRule rule, DeviceData data, List<DataPoint> window) {
        switch (rule.ruleType()) {
            case THRESHOLD:
                return data.value();
            case DELTA:
                return calculateDelta(rule, window);
            case RATE:
                return calculateRate(rule, window);
            case COUNT:
                return calculateCount(rule, window);
            case AVG:
                return calculateAvg(window);
            case OFFLINE:
                return 0.0;
            default:
                throw new IllegalStateException("Unsupported rule type: " + rule.ruleType());
        }
    }

    private double calculateDelta(AlarmRule rule, List<DataPoint> window) {
        if (window.size() < 2) {
            return 0.0;
        }
        double delta = window.get(window.size() - 1).value() - window.get(0).value();
        switch (rule.deltaDirection()) {
            case INCREASE:
                return delta;
            case DECREASE:
                return -delta;
            case ABS:
                return Math.abs(delta);
            default:
                throw new IllegalStateException("Unsupported delta direction: " + rule.deltaDirection());
        }
    }

    private double calculateRate(AlarmRule rule, List<DataPoint> window) {
        if (window.size() < 2) {
            return 0.0;
        }
        DataPoint first = window.get(0);
        DataPoint last = window.get(window.size() - 1);
        long seconds = Duration.between(first.reportTime(), last.reportTime()).getSeconds();
        if (seconds <= 0) {
            return 0.0;
        }
        double delta = last.value() - first.value();
        double minutes = seconds / 60.0;
        switch (rule.deltaDirection()) {
            case INCREASE:
                return delta / minutes;
            case DECREASE:
                return -delta / minutes;
            case ABS:
                return Math.abs(delta) / minutes;
            default:
                throw new IllegalStateException("Unsupported delta direction: " + rule.deltaDirection());
        }
    }

    private double calculateCount(AlarmRule rule, List<DataPoint> window) {
        return window.size();
    }

    private double calculateAvg(List<DataPoint> window) {
        return window.stream()
                .mapToDouble(DataPoint::value)
                .average()
                .orElse(0.0);
    }
}
