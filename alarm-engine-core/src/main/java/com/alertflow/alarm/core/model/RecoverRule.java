package com.alertflow.alarm.core.model;

public final class RecoverRule {
    private final Operator operator;
    private final double threshold;

    public RecoverRule(Operator operator, double threshold) {
        if (operator == null) {
            operator = Operator.LT;
        }
        this.operator = operator;
        this.threshold = threshold;
    }

    public Operator operator() {
        return operator;
    }

    public double threshold() {
        return threshold;
    }
}
