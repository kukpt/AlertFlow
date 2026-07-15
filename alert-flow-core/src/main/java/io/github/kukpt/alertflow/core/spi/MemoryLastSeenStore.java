package io.github.kukpt.alertflow.core.spi;

import io.github.kukpt.alertflow.core.model.DeviceData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryLastSeenStore implements LastSeenStore {
    private final Map<Key, DeviceData> values = new ConcurrentHashMap<>();

    @Override
    public boolean saveLatest(DeviceData data) {
        final boolean[] accepted = {false};
        values.compute(new Key(data.deviceId(), data.metric()), (key, current) ->
        {
            if (current == null || !data.reportTime().isBefore(current.reportTime())) {
                accepted[0] = true;
                return data;
            }
            return current;
        });
        return accepted[0];
    }

    @Override
    public Collection<DeviceData> findAll() {
        return new ArrayList<>(values.values());
    }

    private static final class Key {
        private final String deviceId;
        private final String metric;

        private Key(String deviceId, String metric) {
            this.deviceId = deviceId;
            this.metric = metric;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key)) return false;
            Key that = (Key) other;
            return Objects.equals(deviceId, that.deviceId) && Objects.equals(metric, that.metric);
        }

        @Override
        public int hashCode() { return Objects.hash(deviceId, metric); }
    }
}
