# alarm-engine

轻量级设备数据实时预警引擎，可作为 jar 包被业务系统集成。

## 模块

- `alarm-engine-core`: 核心模型、窗口计算、规则判断、预警状态去重和恢复。
- `alarm-engine-vertx`: Vert.x EventBus 输入输出适配。
- `alarm-engine-example`: EventBus 接入示例。

## EventBus 地址

- 输入: `device.data.report`
- 输出: `alarm.triggered`, `alarm.updated`, `alarm.recovered`

## 快速运行

```bash
mvn test
mvn -pl alarm-engine-example exec:java -Dexec.mainClass=com.alertflow.alarm.example.EventBusExample
```

第一版以内存实现为主，不绑定数据库、通知渠道、管理后台或业务系统表结构。
