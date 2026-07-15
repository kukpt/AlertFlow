package io.github.kukpt.alertflow.redis;

import io.github.kukpt.alertflow.core.model.AlarmStatus;
import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.core.spi.LastSeenStore;
import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.state.AlarmState;
import io.github.kukpt.alertflow.core.window.DataPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "alertflow.redis.integration", matches = "true")
class RedisStoresIntegrationTest {
    private JedisPool pool;

    @BeforeEach
    void resetRedis() {
        int port = Integer.parseInt(System.getProperty("alertflow.redis.port", "6399"));
        pool = new JedisPool("127.0.0.1", port);
        try (Jedis jedis = pool.getResource()) { jedis.flushDB(); }
    }

    @Test
    void stateCompareAndSetAllowsOnlyExpectedVersion() {
        RedisAlarmStateStore store = new RedisAlarmStateStore(pool, true);
        AlarmKey key = AlarmKey.of("D1", "temperature", "R1");
        AlarmState triggered = AlarmState.normal(key).alarming("E1", Instant.parse("2026-07-09T10:00:00Z"),
                Instant.parse("2026-07-09T10:00:00Z"), 81);
        assertTrue(store.compareAndSet(key, 0, triggered));
        assertFalse(store.compareAndSet(key, 0, triggered));
        assertEquals(AlarmStatus.ALARMING, store.get(key).status());
        assertEquals(1, store.findAll().size());
        store.close();
    }

    @Test
    void lastSeenRejectsOlderReportsAndDeduplicatorIsAtomic() {
        RedisLastSeenStore lastSeen = new RedisLastSeenStore(pool);
        lastSeen.saveLatest(data("NEW", 20, "2026-07-09T10:02:00Z"));
        lastSeen.saveLatest(data("OLD", 10, "2026-07-09T10:01:00Z"));
        assertEquals("NEW", lastSeen.findAll().iterator().next().dataId());

        RedisDataDeduplicator deduplicator = new RedisDataDeduplicator(pool, true);
        assertTrue(deduplicator.markIfNew("NEW", Duration.ofMinutes(1)));
        assertFalse(deduplicator.markIfNew("NEW", Duration.ofMinutes(1)));
        deduplicator.close();
    }

    @Test
    void windowKeepsEventTimeOrderAndDropsPointsOutsideLatestWindow() {
        RedisWindowStore store = new RedisWindowStore(pool, true);
        AlarmKey key = AlarmKey.of("D1", "temperature", "R1");
        store.appendAndGet(key, new DataPoint(30, Instant.parse("2026-07-09T10:10:00Z")), Duration.ofMinutes(5));
        List<DataPoint> points = store.appendAndGet(key, new DataPoint(20, Instant.parse("2026-07-09T10:08:00Z")), Duration.ofMinutes(5));
        assertEquals(2, points.size());
        assertEquals(20, points.get(0).value());
        points = store.appendAndGet(key, new DataPoint(10, Instant.parse("2026-07-09T10:01:00Z")), Duration.ofMinutes(5));
        assertEquals(2, points.size());
        store.close();
    }

    private DeviceData data(String id, double value, String time) {
        return new DeviceData(id, "D1", "sensor", "temperature", value, "C", Instant.parse(time), Collections.emptyMap());
    }
}
