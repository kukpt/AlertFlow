package io.github.kukpt.alertflow.redis;

import io.github.kukpt.alertflow.core.model.AlarmStatus;
import io.github.kukpt.alertflow.core.spi.AlarmStateStore;
import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.state.AlarmState;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class RedisAlarmStateStore implements AlarmStateStore, AutoCloseable {
    private static final String INDEX = "alertflow:state:index";
    private static final String CAS =
            "local current=redis.call('HGET',KEYS[1],'version'); "
                    + "if current and tonumber(current) ~= tonumber(ARGV[1]) then return 0 end; "
                    + "if (not current) and tonumber(ARGV[1]) ~= 0 then return 0 end; "
                    + "redis.call('HMSET',KEYS[1],'deviceId',ARGV[2],'metric',ARGV[3],'ruleId',ARGV[4],"
                    + "'status',ARGV[5],'eventId',ARGV[6],'firstTriggerTime',ARGV[7],'lastTriggerTime',ARGV[8],"
                    + "'lastWindowValue',ARGV[9],'version',ARGV[10]); redis.call('SADD',KEYS[2],KEYS[1]); return 1";
    private final JedisPool pool;
    private final boolean closePool;

    public RedisAlarmStateStore(JedisPool pool) { this(pool, false); }
    public RedisAlarmStateStore(JedisPool pool, boolean closePool) { this.pool = pool; this.closePool = closePool; }

    @Override
    public AlarmState get(AlarmKey key) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> values = jedis.hgetAll(key(key));
            return values.isEmpty() ? AlarmState.normal(key) : decode(values);
        }
    }

    @Override
    public void save(AlarmState state) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(key(state.key()));
            if (!compareAndSetWith(jedis, state.key(), 0L, state)) throw new IllegalStateException("Unable to save alarm state");
        }
    }

    @Override
    public boolean compareAndSet(AlarmKey key, long expectedVersion, AlarmState next) {
        try (Jedis jedis = pool.getResource()) { return compareAndSetWith(jedis, key, expectedVersion, next); }
    }

    @Override
    public Collection<AlarmState> findAll() {
        try (Jedis jedis = pool.getResource()) {
            List<AlarmState> states = new ArrayList<>();
            for (String redisKey : jedis.smembers(INDEX)) {
                Map<String, String> values = jedis.hgetAll(redisKey);
                if (!values.isEmpty()) states.add(decode(values));
            }
            return states;
        }
    }

    private boolean compareAndSetWith(Jedis jedis, AlarmKey key, long expectedVersion, AlarmState state) {
        Object result = jedis.eval(CAS, java.util.Arrays.asList(key(key), INDEX), java.util.Arrays.asList(
                String.valueOf(expectedVersion), RedisKeys.encode(key.deviceId()), RedisKeys.encode(key.metric()), RedisKeys.encode(key.ruleId()),
                state.status().name(), nullable(state.eventId()), instant(state.firstTriggerTime()), instant(state.lastTriggerTime()),
                String.valueOf(state.lastWindowValue()), String.valueOf(state.version())));
        return Long.valueOf(1L).equals(result);
    }

    private static AlarmState decode(Map<String, String> value) {
        AlarmKey key = AlarmKey.of(RedisKeys.decode(value.get("deviceId")), RedisKeys.decode(value.get("metric")), RedisKeys.decode(value.get("ruleId")));
        return new AlarmState(key, AlarmStatus.valueOf(value.get("status")), empty(value.get("eventId")),
                parseInstant(value.get("firstTriggerTime")), parseInstant(value.get("lastTriggerTime")),
                Double.parseDouble(value.get("lastWindowValue")), Long.parseLong(value.get("version")));
    }

    private static String key(AlarmKey key) { return "alertflow:state:" + RedisKeys.encode(key.deviceId()) + ':' + RedisKeys.encode(key.metric()) + ':' + RedisKeys.encode(key.ruleId()); }
    private static String nullable(String value) { return value == null ? "" : value; }
    private static String empty(String value) { return value == null || value.isEmpty() ? null : value; }
    private static String instant(Instant value) { return value == null ? "" : value.toString(); }
    private static Instant parseInstant(String value) { return value == null || value.isEmpty() ? null : Instant.parse(value); }
    @Override public void close() { if (closePool) pool.close(); }
}
