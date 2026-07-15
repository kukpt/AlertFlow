package io.github.kukpt.alertflow.vertx;

import io.github.kukpt.alertflow.core.spi.*;
import io.github.kukpt.alertflow.runtime.AlertFlowRuntime;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

import java.util.Objects;

/** Vert.x lifecycle adapter. Blocking stores are isolated from the event loop. */
public final class VertxAlarmEngineStarter implements AutoCloseable {
    private final Vertx vertx;
    private final RuleProvider ruleProvider;
    private AlarmStateStore stateStore = new MemoryAlarmStateStore();
    private WindowStore windowStore = new MemoryWindowStore();
    private LastSeenStore lastSeenStore = new MemoryLastSeenStore();
    private DataDeduplicator dataDeduplicator;
    private AlarmEventSink eventSink;
    private VertxAlertFlowOptions options = new VertxAlertFlowOptions();
    private AlertFlowRuntime runtime;
    private MessageConsumer<JsonObject> consumer;

    public VertxAlarmEngineStarter(Vertx vertx, RuleProvider ruleProvider) {
        this.vertx = Objects.requireNonNull(vertx, "vertx");
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider");
    }

    public VertxAlarmEngineStarter(Vertx vertx, RuleProvider ruleProvider, AlarmStateStore stateStore) {
        this(vertx, ruleProvider);
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
    }

    public VertxAlarmEngineStarter options(VertxAlertFlowOptions value) { options = Objects.requireNonNull(value); return this; }
    public VertxAlarmEngineStarter alarmStateStore(AlarmStateStore value) { stateStore = Objects.requireNonNull(value); return this; }
    public VertxAlarmEngineStarter windowStore(WindowStore value) { windowStore = Objects.requireNonNull(value); return this; }
    public VertxAlarmEngineStarter lastSeenStore(LastSeenStore value) { lastSeenStore = Objects.requireNonNull(value); return this; }
    public VertxAlarmEngineStarter dataDeduplicator(DataDeduplicator value) { dataDeduplicator = value; return this; }
    public VertxAlarmEngineStarter eventSink(AlarmEventSink value) { eventSink = Objects.requireNonNull(value); return this; }

    public synchronized MessageConsumer<JsonObject> start() {
        if (consumer != null) return consumer;
        AlertFlowRuntime.Builder builder = AlertFlowRuntime.builder()
                .options(options.runtimeOptions()).ruleProvider(ruleProvider).alarmStateStore(stateStore)
                .windowStore(windowStore).lastSeenStore(lastSeenStore)
                .eventSink(eventSink == null ? new EventBusAlarmEventPublisher(vertx, options) : eventSink);
        if (dataDeduplicator != null) builder.dataDeduplicator(dataDeduplicator);
        runtime = builder.build();
        runtime.start();
        consumer = new EventBusDeviceDataConsumer(vertx, runtime, options).start();
        return consumer;
    }

    @Override
    public synchronized void close() {
        if (consumer != null) consumer.unregister();
        if (runtime != null) runtime.close();
        consumer = null;
        runtime = null;
    }
}
