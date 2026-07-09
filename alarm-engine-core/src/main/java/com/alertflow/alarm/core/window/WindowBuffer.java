package com.alertflow.alarm.core.window;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class WindowBuffer {
    private final Deque<DataPoint> points = new ArrayDeque<>();

    public synchronized List<DataPoint> add(double value, Instant reportTime, Duration windowSize) {
        points.addLast(new DataPoint(value, reportTime));
        evictExpired(reportTime, windowSize);
        return snapshot();
    }

    public synchronized List<DataPoint> snapshot() {
        return new ArrayList<>(points);
    }

    private void evictExpired(Instant reportTime, Duration windowSize) {
        if (windowSize == null || windowSize.isZero() || windowSize.isNegative()) {
            while (points.size() > 1) {
                points.removeFirst();
            }
            return;
        }
        Instant minTime = reportTime.minus(windowSize);
        while (!points.isEmpty() && points.peekFirst().reportTime().isBefore(minTime)) {
            points.removeFirst();
        }
    }
}
