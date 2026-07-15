package io.github.kukpt.alertflow.runtime;

import io.github.kukpt.alertflow.core.AlarmEngine;
import io.github.kukpt.alertflow.core.model.AlarmEvent;
import io.github.kukpt.alertflow.core.model.DeviceData;
import io.github.kukpt.alertflow.core.rule.RuleEvaluator;
import io.github.kukpt.alertflow.core.spi.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AlertFlowRuntime implements AutoCloseable {
    private final AlarmEngine engine;
    private final AlertFlowOptions options;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private ScheduledFuture<?> offlineTask;

    private AlertFlowRuntime(Builder builder) {
        options = builder.options;
        ownsScheduler = builder.scheduler == null;
        scheduler = ownsScheduler
                ? Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "alert-flow-offline-scan"); t.setDaemon(true); return t; })
                : builder.scheduler;
        AlarmEventSink sink = new ThrottledAlarmEventSink(builder.eventSink, options);
        engine = new AlarmEngine(builder.ruleProvider, builder.stateStore, sink, new RuleEvaluator(),
                builder.windowStore, builder.lastSeenStore, builder.dataDeduplicator, options.deduplicationRetention());
    }

    public static Builder builder() { return new Builder(); }

    public synchronized void start() {
        if (closed.get()) throw new IllegalStateException("AlertFlowRuntime is closed");
        if (!started.compareAndSet(false, true)) return;
        if (!options.offlineScanInterval().isZero()) {
            long interval = Math.max(1L, options.offlineScanInterval().toMillis());
            offlineTask = scheduler.scheduleWithFixedDelay(() -> {
                try { engine.checkOffline(Instant.now()); }
                catch (RuntimeException ignored) { /* keep subsequent scans alive; adapters expose failures through their observability layer */ }
            }, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    public List<AlarmEvent> handle(DeviceData data) {
        if (!started.get()) throw new IllegalStateException("AlertFlowRuntime is not started");
        return engine.handle(data);
    }

    public List<AlarmEvent> checkOffline(Instant now) { return engine.checkOffline(now); }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        started.set(false);
        if (offlineTask != null) offlineTask.cancel(false);
        if (ownsScheduler) scheduler.shutdown();
    }

    public static final class Builder {
        private RuleProvider ruleProvider;
        private AlarmStateStore stateStore = new MemoryAlarmStateStore();
        private AlarmEventSink eventSink;
        private WindowStore windowStore = new MemoryWindowStore();
        private LastSeenStore lastSeenStore = new MemoryLastSeenStore();
        private DataDeduplicator dataDeduplicator;
        private AlertFlowOptions options = new AlertFlowOptions();
        private ScheduledExecutorService scheduler;

        public Builder ruleProvider(RuleProvider value) { ruleProvider = value; return this; }
        public Builder alarmStateStore(AlarmStateStore value) { stateStore = value; return this; }
        public Builder eventSink(AlarmEventSink value) { eventSink = value; return this; }
        public Builder windowStore(WindowStore value) { windowStore = value; return this; }
        public Builder lastSeenStore(LastSeenStore value) { lastSeenStore = value; return this; }
        public Builder dataDeduplicator(DataDeduplicator value) { dataDeduplicator = value; return this; }
        public Builder options(AlertFlowOptions value) { options = value; return this; }
        public Builder scheduler(ScheduledExecutorService value) { scheduler = value; return this; }

        public AlertFlowRuntime build() {
            Objects.requireNonNull(ruleProvider, "ruleProvider");
            Objects.requireNonNull(stateStore, "alarmStateStore");
            Objects.requireNonNull(eventSink, "eventSink");
            Objects.requireNonNull(windowStore, "windowStore");
            Objects.requireNonNull(lastSeenStore, "lastSeenStore");
            Objects.requireNonNull(options, "options");
            return new AlertFlowRuntime(this);
        }
    }
}
