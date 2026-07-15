package io.github.kukpt.alertflow.vertx;

public final class EventBusAddresses {
    public static final String DEVICE_DATA_REPORT = "device.data.report";
    public static final String ALARM_TRIGGERED = "alarm.triggered";
    public static final String ALARM_RECOVERED = "alarm.recovered";
    public static final String ALARM_UPDATED = "alarm.updated";
    public static final String INVALID_INPUT = "alarm.input.invalid";

    private EventBusAddresses() {
    }
}
