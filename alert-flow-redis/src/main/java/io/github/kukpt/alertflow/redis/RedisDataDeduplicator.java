package io.github.kukpt.alertflow.redis;

import io.github.kukpt.alertflow.core.spi.DataDeduplicator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;

public final class RedisDataDeduplicator implements DataDeduplicator, AutoCloseable {
    private final JedisPool pool;
    private final boolean closePool;
    public RedisDataDeduplicator(JedisPool pool) { this(pool, false); }
    public RedisDataDeduplicator(JedisPool pool, boolean closePool) { this.pool = pool; this.closePool = closePool; }

    @Override
    public boolean markIfNew(String dataId, Duration retention) {
        long ttl = Math.max(1L, retention.toMillis());
        try (Jedis jedis = pool.getResource()) {
            return "OK".equals(jedis.set("alertflow:dedup:" + RedisKeys.encode(dataId), "1", SetParams.setParams().nx().px(ttl)));
        }
    }
    @Override public void close() { if (closePool) pool.close(); }
}
