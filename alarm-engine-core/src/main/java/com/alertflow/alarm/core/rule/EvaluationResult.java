package com.alertflow.alarm.core.rule;

public final class EvaluationResult {
    private final boolean triggered;
    private final double windowValue;

    public EvaluationResult(boolean triggered, double windowValue) {
        this.triggered = triggered;
        this.windowValue = windowValue;
    }

    public boolean triggered() {
        return triggered;
    }

    public double windowValue() {
        return windowValue;
    }
}
