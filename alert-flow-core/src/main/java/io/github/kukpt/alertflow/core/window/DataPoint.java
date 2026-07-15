package io.github.kukpt.alertflow.core.window;

import java.time.Instant;

public final class DataPoint {
    private final double value;
    private final Instant reportTime;

    public DataPoint(double value, Instant reportTime) {
        this.value = value;
        this.reportTime = reportTime;
    }

    public double value() {
        return value;
    }

    public Instant reportTime() {
        return reportTime;
    }
}
