package io.github.kukpt.alertflow.core.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class DeviceData {
    private final String dataId;
    private final String deviceId;
    private final String deviceType;
    private final String metric;
    private final double value;
    private final String unit;
    private final Instant reportTime;
    private final Map<String, String> tags;

    public DeviceData(String deviceId, String deviceType, String metric, double value, String unit, Instant reportTime, Map<String, String> tags) {
        this(null, deviceId, deviceType, metric, value, unit, reportTime, tags);
    }

    public DeviceData(String dataId, String deviceId, String deviceType, String metric, double value, String unit, Instant reportTime, Map<String, String> tags) {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(reportTime, "reportTime");
        this.dataId = dataId;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.metric = metric;
        this.value = value;
        this.unit = unit;
        this.reportTime = reportTime;
        this.tags = tags == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(tags));
    }

    public String dataId() { return dataId; }

    public String deviceId() {
        return deviceId;
    }

    public String deviceType() {
        return deviceType;
    }

    public String metric() {
        return metric;
    }

    public double value() {
        return value;
    }

    public String unit() {
        return unit;
    }

    public Instant reportTime() {
        return reportTime;
    }

    public Map<String, String> tags() {
        return tags;
    }
}
