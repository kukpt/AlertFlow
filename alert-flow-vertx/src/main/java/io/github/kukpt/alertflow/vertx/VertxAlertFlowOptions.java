package io.github.kukpt.alertflow.vertx;

import io.github.kukpt.alertflow.runtime.AlertFlowOptions;

public final class VertxAlertFlowOptions {
    private String inputAddress = EventBusAddresses.DEVICE_DATA_REPORT;
    private String triggeredAddress = EventBusAddresses.ALARM_TRIGGERED;
    private String updatedAddress = EventBusAddresses.ALARM_UPDATED;
    private String recoveredAddress = EventBusAddresses.ALARM_RECOVERED;
    private String invalidInputAddress = EventBusAddresses.INVALID_INPUT;
    private boolean orderedWorkerExecution = true;
    private AlertFlowOptions runtimeOptions = new AlertFlowOptions();

    public String inputAddress() { return inputAddress; }
    public String triggeredAddress() { return triggeredAddress; }
    public String updatedAddress() { return updatedAddress; }
    public String recoveredAddress() { return recoveredAddress; }
    public String invalidInputAddress() { return invalidInputAddress; }
    public boolean orderedWorkerExecution() { return orderedWorkerExecution; }
    public AlertFlowOptions runtimeOptions() { return runtimeOptions; }

    public VertxAlertFlowOptions setInputAddress(String v) { inputAddress = required(v, "inputAddress"); return this; }
    public VertxAlertFlowOptions setTriggeredAddress(String v) { triggeredAddress = required(v, "triggeredAddress"); return this; }
    public VertxAlertFlowOptions setUpdatedAddress(String v) { updatedAddress = required(v, "updatedAddress"); return this; }
    public VertxAlertFlowOptions setRecoveredAddress(String v) { recoveredAddress = required(v, "recoveredAddress"); return this; }
    public VertxAlertFlowOptions setInvalidInputAddress(String v) { invalidInputAddress = required(v, "invalidInputAddress"); return this; }
    public VertxAlertFlowOptions setOrderedWorkerExecution(boolean v) { orderedWorkerExecution = v; return this; }
    public VertxAlertFlowOptions setRuntimeOptions(AlertFlowOptions v) { runtimeOptions = java.util.Objects.requireNonNull(v); return this; }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
