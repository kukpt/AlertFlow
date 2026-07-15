package io.github.kukpt.alertflow.vertx;

import io.github.kukpt.alertflow.core.spi.AlarmStateStore;
import io.github.kukpt.alertflow.core.spi.DataDeduplicator;
import io.github.kukpt.alertflow.core.spi.LastSeenStore;
import io.github.kukpt.alertflow.core.spi.RuleProvider;
import io.github.kukpt.alertflow.core.spi.WindowStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

import java.util.Objects;

/** Standard deployable Verticle for embedding AlertFlow in an existing Vert.x application. */
public final class AlertFlowVerticle extends AbstractVerticle {
    private final RuleProvider ruleProvider;
    private final VertxAlertFlowOptions options;
    private AlarmStateStore stateStore;
    private WindowStore windowStore;
    private LastSeenStore lastSeenStore;
    private DataDeduplicator dataDeduplicator;
    private VertxAlarmEngineStarter starter;

    public AlertFlowVerticle(RuleProvider ruleProvider, VertxAlertFlowOptions options) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider");
        this.options = Objects.requireNonNull(options, "options");
    }

    public AlertFlowVerticle alarmStateStore(AlarmStateStore value) { stateStore = value; return this; }
    public AlertFlowVerticle windowStore(WindowStore value) { windowStore = value; return this; }
    public AlertFlowVerticle lastSeenStore(LastSeenStore value) { lastSeenStore = value; return this; }
    public AlertFlowVerticle dataDeduplicator(DataDeduplicator value) { dataDeduplicator = value; return this; }

    @Override
    public void start(Promise<Void> startPromise) {
        try {
            starter = new VertxAlarmEngineStarter(vertx, ruleProvider).options(options);
            if (stateStore != null) starter.alarmStateStore(stateStore);
            if (windowStore != null) starter.windowStore(windowStore);
            if (lastSeenStore != null) starter.lastSeenStore(lastSeenStore);
            if (dataDeduplicator != null) starter.dataDeduplicator(dataDeduplicator);
            starter.start();
            startPromise.complete();
        } catch (RuntimeException error) {
            startPromise.fail(error);
        }
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        try {
            if (starter != null) starter.close();
            stopPromise.complete();
        } catch (RuntimeException error) {
            stopPromise.fail(error);
        }
    }
}
