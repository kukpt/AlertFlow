package io.github.kukpt.alertflow.redis;

import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.core.spi.LastSeenStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class RedisLastSeenStore implements LastSeenStore, AutoCloseable {
    private static final String INDEX = "alertflow:lastseen:index";
    private static final String SAVE_LATEST =
            "local current=redis.call('HGET',KEYS[1],'reportEpoch'); "
                    + "if current and tonumber(current) > tonumber(ARGV[1]) then return 0 end; "
                    + "redis.call('HMSET',KEYS[1],'reportEpoch',ARGV[1],'dataId',ARGV[2],'deviceId',ARGV[3],"
                    + "'deviceType',ARGV[4],'metric',ARGV[5],'value',ARGV[6],'unit',ARGV[7],'reportTime',ARGV[8],'tags',ARGV[9]); "
                    + "redis.call('SADD',KEYS[2],KEYS[1]); return 1";
    private final JedisPool pool;
    private final boolean closePool;

    public RedisLastSeenStore(JedisPool pool) { this(pool, false); }
    public RedisLastSeenStore(JedisPool pool, boolean closePool) { this.pool = pool; this.closePool = closePool; }

    @Override
    public boolean saveLatest(DeviceData data) {
        try (Jedis jedis = pool.getResource()) {
            Object result = jedis.eval(SAVE_LATEST, java.util.Arrays.asList(key(data), INDEX), java.util.Arrays.asList(
                    String.valueOf(data.reportTime().toEpochMilli()), nullable(data.dataId()), RedisKeys.encode(data.deviceId()),
                    nullable(data.deviceType()), RedisKeys.encode(data.metric()), String.valueOf(data.value()), nullable(data.unit()),
                    data.reportTime().toString(), encodeTags(data.tags())));
            return Long.valueOf(1L).equals(result);
        }
    }

    @Override
    public Collection<DeviceData> findAll() {
        try (Jedis jedis = pool.getResource()) {
            Collection<DeviceData> result = new ArrayList<>();
            for (String redisKey : jedis.smembers(INDEX)) {
                Map<String, String> value = jedis.hgetAll(redisKey);
                if (!value.isEmpty()) result.add(decode(value));
            }
            return result;
        }
    }

    private static DeviceData decode(Map<String, String> value) {
        return new DeviceData(empty(value.get("dataId")), RedisKeys.decode(value.get("deviceId")), empty(value.get("deviceType")),
                RedisKeys.decode(value.get("metric")), Double.parseDouble(value.get("value")), empty(value.get("unit")),
                Instant.parse(value.get("reportTime")), decodeTags(value.get("tags")));
    }

    private static String encodeTags(Map<String, String> tags) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (out.length() > 0) out.append(',');
            out.append(RedisKeys.encode(entry.getKey())).append(':').append(RedisKeys.encode(entry.getValue()));
        }
        return out.toString();
    }

    private static Map<String, String> decodeTags(String value) {
        if (value == null || value.isEmpty()) return Collections.emptyMap();
        Map<String, String> tags = new HashMap<>();
        for (String part : value.split(",")) {
            String[] pair = part.split(":", 2);
            tags.put(RedisKeys.decode(pair[0]), RedisKeys.decode(pair[1]));
        }
        return tags;
    }

    private static String key(DeviceData data) { return "alertflow:lastseen:" + RedisKeys.encode(data.deviceId()) + ':' + RedisKeys.encode(data.metric()); }
    private static String nullable(String value) { return value == null ? "" : value; }
    private static String empty(String value) { return value == null || value.isEmpty() ? null : value; }
    @Override public void close() { if (closePool) pool.close(); }
}
