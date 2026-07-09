package com.alertflow.alarm.vertx;

import com.alertflow.alarm.core.AlarmEngine;
import com.alertflow.alarm.core.spi.AlarmStateStore;
import com.alertflow.alarm.core.spi.MemoryAlarmStateStore;
import com.alertflow.alarm.core.spi.RuleProvider;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public final class VertxAlarmEngineStarter {
    private final Vertx vertx;
    private final RuleProvider ruleProvider;
    private final AlarmStateStore stateStore;

    public VertxAlarmEngineStarter(Vertx vertx, RuleProvider ruleProvider) {
        this(vertx, ruleProvider, new MemoryAlarmStateStore());
    }

    public VertxAlarmEngineStarter(Vertx vertx, RuleProvider ruleProvider, AlarmStateStore stateStore) {
        this.vertx = vertx;
        this.ruleProvider = ruleProvider;
        this.stateStore = stateStore;
    }

    public MessageConsumer<JsonObject> start() {
        AlarmEngine engine = new AlarmEngine(ruleProvider, stateStore, new EventBusAlarmEventPublisher(vertx));
        return new EventBusDeviceDataConsumer(vertx, engine).start();
    }
}
