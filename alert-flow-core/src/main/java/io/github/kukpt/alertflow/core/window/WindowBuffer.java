package io.github.kukpt.alertflow.core.window;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WindowBuffer {
    private final List<DataPoint> points = new ArrayList<>();

    public synchronized List<DataPoint> add(double value, Instant reportTime, Duration windowSize) {
        points.add(new DataPoint(value, reportTime));
        points.sort(Comparator.comparing(DataPoint::reportTime));
        evictExpired(windowSize);
        return snapshot();
    }

    public synchronized List<DataPoint> snapshot() {
        return new ArrayList<>(points);
    }

    private void evictExpired(Duration windowSize) {
        if (windowSize == null || windowSize.isZero() || windowSize.isNegative()) {
            while (points.size() > 1) {
                points.remove(0);
            }
            return;
        }
        Instant minTime = points.get(points.size() - 1).reportTime().minus(windowSize);
        while (!points.isEmpty() && points.get(0).reportTime().isBefore(minTime)) {
            points.remove(0);
        }
    }
}
