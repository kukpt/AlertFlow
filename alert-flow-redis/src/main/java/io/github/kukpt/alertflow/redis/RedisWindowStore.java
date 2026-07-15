package io.github.kukpt.alertflow.redis;

import io.github.kukpt.alertflow.core.spi.WindowStore;
import io.github.kukpt.alertflow.core.state.AlarmKey;
import io.github.kukpt.alertflow.core.window.DataPoint;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed sliding windows. The Lua script makes insertion, expiry cleanup,
 * TTL refresh and reading the window one atomic operation.
 */
public final class RedisWindowStore implements WindowStore, AutoCloseable {
    private static final String APPEND_AND_GET =
            "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]); "
                    + "local newest = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES'); "
                    + "local cutoff = tonumber(newest[2]) - tonumber(ARGV[3]); "
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. cutoff); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[4]); "
                    + "return redis.call('ZRANGE', KEYS[1], 0, -1, 'WITHSCORES');";
    private static final long TTL_BUFFER_MILLIS = Duration.ofMinutes(1).toMillis();

    private final JedisPool pool;
    private final boolean closePool;

    public RedisWindowStore(JedisPool pool) {
        this(pool, false);
    }

    public RedisWindowStore(JedisPool pool, boolean closePool) {
        this.pool = pool;
        this.closePool = closePool;
    }

    @Override
    public List<DataPoint> appendAndGet(AlarmKey key, DataPoint point, Duration windowSize) {
        long pointTime = point.reportTime().toEpochMilli();
        long windowMillis = validWindowMillis(windowSize);
        long ttlMillis = Math.max(TTL_BUFFER_MILLIS, windowMillis + TTL_BUFFER_MILLIS);
        String member = point.value() + ":" + UUID.randomUUID();

        try (Jedis jedis = pool.getResource()) {
            Object result = jedis.eval(
                    APPEND_AND_GET,
                    Collections.singletonList(redisKey(key)),
                    asStrings(pointTime, member, windowMillis, ttlMillis)
            );
            return toDataPoints(result);
        }
    }

    @Override
    public void close() {
        if (closePool) {
            pool.close();
        }
    }

    private static long validWindowMillis(Duration windowSize) {
        if (windowSize == null || windowSize.isZero() || windowSize.isNegative()) {
            return 0;
        }
        return windowSize.toMillis();
    }

    private static List<String> asStrings(long pointTime, String member, long windowMillis, long ttlMillis) {
        List<String> values = new ArrayList<>();
        values.add(String.valueOf(pointTime));
        values.add(member);
        values.add(String.valueOf(windowMillis));
        values.add(String.valueOf(ttlMillis));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static List<DataPoint> toDataPoints(Object result) {
        List<Object> raw = (List<Object>) result;
        List<DataPoint> points = new ArrayList<>();
        for (int index = 0; index < raw.size(); index += 2) {
            String member = raw.get(index).toString();
            double value = Double.parseDouble(member.substring(0, member.indexOf(':')));
            long reportTime = Double.valueOf(raw.get(index + 1).toString()).longValue();
            points.add(new DataPoint(value, Instant.ofEpochMilli(reportTime)));
        }
        return points;
    }

    private static String redisKey(AlarmKey key) {
        return "alarm:window:{" + encode(key.deviceId()) + ':' + encode(key.metric()) + ':' + encode(key.ruleId()) + '}';
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
