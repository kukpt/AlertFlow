package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.model.DeviceData;

import java.util.Collection;

/** Stores the latest data point for every device and metric used by OFFLINE rules. */
public interface LastSeenStore {
    /** Updates the value only when the incoming report is not older than the stored report. */
    /** @return true when accepted; false when the report is older than the stored value. */
    boolean saveLatest(DeviceData data);

    Collection<DeviceData> findAll();
}
