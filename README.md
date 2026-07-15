# alert-flow

`alert-flow` 是一个轻量级设备数据实时预警引擎，可作为 jar 嵌入 Vert.x 项目，也可由相同 runtime 组装为独立服务。

它只负责接收设备数据、匹配规则、维护时间窗口、判断触发/恢复、输出预警事件。设备管理、页面、通知、权限、数据库表结构、报表等业务能力不属于本项目。

## Agent 快速上下文

如果你是 coding agent，优先读这个 README，再按任务需要读对应文件。

- 核心入口: `alert-flow-core/src/main/java/io/github/kukpt/alertflow/core/AlarmEngine.java`
- 运行时: `alert-flow-runtime/src/main/java/io/github/kukpt/alertflow/runtime/AlertFlowRuntime.java`
- Vert.x 适配: `alert-flow-vertx/src/main/java/io/github/kukpt/alertflow/vertx/`
- Redis 状态后端: `alert-flow-redis/src/main/java/io/github/kukpt/alertflow/redis/`
- 使用示例: `alert-flow-example/src/main/java/io/github/kukpt/alertflow/example/EventBusExample.java`
- 原始需求说明: `AGENT.MD`

改动原则：

- `alert-flow-core` 不允许依赖 Vert.x、Spring、数据库或消息中间件。
- 业务系统负责保存预警、发送通知、管理设备和规则来源。
- 新增输入/输出通道时，放在独立适配模块，不要污染 core。
- 规则判断、窗口计算、状态流转必须保持可单元测试。
- 当前代码兼容 Java 8，不要引入 `record`、switch expression、`List.of` 等较新语法，除非同步升级构建目标。

## 模块结构

```text
alert-flow
├── alert-flow-core
├── alert-flow-runtime
├── alert-flow-vertx
├── alert-flow-redis
└── alert-flow-example
```

### alert-flow-core

纯核心模块，不依赖 Vert.x。

职责：

- 定义 `DeviceData`、`AlarmRule`、`AlarmEvent`
- 定义 `RuleType`、`Operator`、`AlarmStatus`、`AlarmEventType`
- 维护滑动窗口
- 计算规则结果
- 管理预警状态并做事件去重
- 判断恢复
- 暴露 `RuleProvider`、`AlarmStateStore`、`WindowStore`、`AlarmEventSink` 扩展接口
- 提供内存实现用于第一版和测试

### alert-flow-vertx

Vert.x EventBus 适配模块。

职责：

- 消费设备数据地址 `device.data.report`
- 将 JSON 转为 `DeviceData`
- 调用 `AlarmEngine.handle(DeviceData)`
- 按事件类型发布到 `alarm.triggered`、`alarm.updated`、`alarm.recovered`
- 使用 worker 执行同步 Engine/Redis 调用，避免阻塞 Event Loop
- 提供可配置地址、错误地址和 `AlertFlowVerticle`

### alert-flow-runtime

负责 Engine 组装、生命周期、OFFLINE 定时扫描、输入幂等和 UPDATED 输出策略。

### alert-flow-redis

提供 `RedisWindowStore`、`RedisAlarmStateStore`、`RedisLastSeenStore` 和 `RedisDataDeduplicator`。Redis 保存的是计算状态，不是业务告警记录。

### alert-flow-example

最小接入示例，演示如何创建规则、启动 Vert.x 适配并发送设备数据。

## 核心流程

```text
设备数据上报
   ↓
EventBusDeviceDataConsumer
   ↓
AlarmEngine.handle(DeviceData)
   ↓
RuleProvider.findRules(data)
   ↓
WindowBuffer.add(...)
   ↓
RuleEvaluator.evaluate(...)
   ↓
AlarmStateStore 状态流转
   ↓
AlarmEventSink.emit(event)
```

## EventBus 地址

输入：

```text
device.data.report
```

输出：

```text
alarm.triggered
alarm.updated
alarm.recovered
```

无效输入：

```text
alarm.input.invalid
```

## 数据模型

### DeviceData

设备上报数据。

关键字段：

- `deviceId`: 设备 ID
- `dataId`: 可选幂等 ID；配置 Deduplicator 后重复 ID 只处理一次
- `deviceType`: 设备类型
- `metric`: 指标名
- `value`: 当前值
- `unit`: 单位
- `reportTime`: 上报时间，核心使用 `Instant`
- `tags`: 扩展标签

同一 `deviceId + metric` 下，早于当前最后上报时间的乱序消息会被拒绝，不参与规则计算。

### AlarmRule

预警规则。

关键字段：

- `ruleId`: 规则 ID
- `ruleName`: 规则名
- `deviceId`: 指定设备，可为空
- `deviceType`: 指定设备类型，可为空
- `metric`: 指标名
- `ruleType`: 规则类型
- `windowSize`: 窗口大小
- `operator`: 比较操作符
- `threshold`: 阈值
- `level`: 预警等级
- `enabled`: 是否启用
- `recoverRule`: 恢复规则，可为空
- `deltaDirection`: 变化方向，默认 `ABS`

### AlarmEvent

预警输出事件。

事件类型：

- `TRIGGERED`: 从正常或已恢复进入预警
- `UPDATED`: 已在预警中，继续满足触发条件
- `RECOVERED`: 已在预警中，满足恢复条件

## 规则类型

当前支持：

- `THRESHOLD`: 当前值阈值，例如温度 `> 80`
- `DELTA`: 窗口内变化量，默认绝对值变化
- `RATE`: 窗口内变化速率，单位按每分钟计算
- `COUNT`: 窗口内数据点数量
- `AVG`: 窗口平均值
- `OFFLINE`: 离线判断，通过 `AlarmEngine.checkOffline(Instant now)` 主动扫描

注意：

- 普通数据进入使用 `AlarmEngine.handle(DeviceData)`。
- 离线规则不是普通数据点判断，调用方应定时调用 `checkOffline`。
- 如果 `recoverRule` 为空，恢复条件默认为“不再满足触发条件”。

## 状态流转

状态 key：

```text
deviceId + metric + ruleId
```

状态：

```text
NORMAL
ALARMING
RECOVERED
```

流转：

```text
NORMAL    -> 满足触发条件 -> ALARMING  -> 输出 TRIGGERED
ALARMING  -> 继续满足条件 -> ALARMING  -> 输出 UPDATED
ALARMING  -> 满足恢复条件 -> RECOVERED -> 输出 RECOVERED
RECOVERED -> 再次满足条件 -> ALARMING  -> 输出 TRIGGERED
```

不要在同一个持续超限过程中重复创建新预警事件 ID。

## 扩展点

core 只定义接口，不绑定具体基础设施。

- `RuleProvider`: 规则来源
- `AlarmStateStore`: 预警状态存储
- `AlarmEventSink`: 事件输出
- `WindowStore`: 滑动窗口状态
- `LastSeenStore`: 最后上报数据，用于 OFFLINE 检测
- `DataDeduplicator`: 输入幂等

已有内存实现：

- `MemoryRuleProvider`
- `MemoryAlarmStateStore`
- `MemoryWindowStore`
- `MemoryLastSeenStore`
- `MemoryDataDeduplicator`
- `CollectingAlarmEventSink`

已有 Vert.x 实现：

- `EventBusDeviceDataConsumer`
- `EventBusAlarmEventPublisher`
- `VertxAlarmEngineStarter`

Redis 生产状态实现已经位于 `alert-flow-redis`。Kafka、MQTT、HTTP 等输入输出协议应继续放在独立适配模块，不要放进 core。

## 快速运行

运行测试：

```bash
mvn test
```

运行示例：

```bash
mvn -pl alert-flow-example exec:java -Dexec.mainClass=io.github.kukpt.alertflow.example.EventBusExample
```

完整清理并测试：

```bash
mvn clean test
```

当前本机验证过：

```text
mvn clean test
BUILD SUCCESS
```

Redis 集成测试默认跳过；本机启动 Redis 后可显式执行：

```bash
mvn -pl alert-flow-redis -am test \
  -Dalertflow.redis.integration=true \
  -Dalertflow.redis.port=6379
```

## 示例输入

```json
{
  "dataId": "report-D001-20260709-100000",
  "deviceId": "D001",
  "deviceType": "displacement_sensor",
  "metric": "displacement",
  "value": 120.5,
  "unit": "mm",
  "reportTime": "2026-07-09T10:00:00Z"
}
```

示例规则：

```java
AlarmRule rule = new AlarmRule(
        "R001",
        "5分钟位移变化预警",
        null,
        "displacement_sensor",
        "displacement",
        RuleType.DELTA,
        Duration.ofMinutes(5),
        Operator.GTE,
        10.0,
        "HIGH",
        true,
        null,
        DeltaDirection.ABS
);
```

## Coding Agent 任务指南

常见任务入口：

- 新增规则类型: 改 `RuleType` 和 `RuleEvaluator`，补 `AlarmEngineTest`
- 修改状态流转: 改 `AlarmEngine` 和 `AlarmState`，补覆盖触发/更新/恢复的测试
- 新增存储实现: 实现 `AlarmStateStore` 或 `WindowStore`，不要修改核心状态模型除非必要
- 新增规则来源: 实现 `RuleProvider`
- 新增事件输出: 实现 `AlarmEventSink`
- 新增接入通道: 新建适配模块或放入对应适配包，避免 core 依赖外部框架
- 修改 EventBus 地址: 改 `EventBusAddresses` 并同步 README

提交前建议检查：

```bash
mvn clean test
git status --short
```

不要做：

- 不要在 core 中引入数据库表结构、业务系统实体或通知渠道
- 不要把页面、权限、报表、多租户放进本项目
- 不要为了未来扩展引入复杂 DSL、脚本引擎或流式计算框架
- 不要提交 `target/`、IDE 配置或本机临时文件
