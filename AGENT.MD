# AlertFlow 项目上下文与开发约束

本文档用于统一 AlertFlow 的产品定位、架构边界、数据职责和后续开发顺序。开发者和 Coding Agent 在修改代码前应先阅读本文档，再根据任务阅读对应模块。

## 1. 项目定位

AlertFlow 是一个轻量级设备数据实时预警引擎，目标是成为可复用的告警计算基础设施，而不是完整的设备管理或监控平台。

它提供两种运行方式：

1. **嵌入模式**：以 jar/starter 形式集成到现有 Vert.x 项目。
2. **独立服务模式**：使用同一套运行时和计算内核，组合 Kafka、MQTT、HTTP 或 EventBus 等适配器独立部署。

两种模式必须复用相同的 core 和 runtime，不维护两套告警逻辑。

AlertFlow 的核心职责是：

- 接收标准化设备数据。
- 匹配预警规则。
- 维护计算所需的时间窗口和运行状态。
- 判断告警触发、持续和恢复。
- 对持续告警去重。
- 输出标准化告警事件。

项目当前以“可嵌入、可测试、边界清晰”为优先目标；在此基础上逐步补齐可持久化、可多实例运行和可观测能力。

## 2. 核心边界

### 2.1 本项目负责

- 设备数据输入模型。
- 预警规则模型和规则计算。
- 滑动时间窗口。
- 告警状态机和事件去重。
- 告警恢复判断。
- 离线检测所需的最后上报状态。
- 计算状态的内存及 Redis 实现。
- 标准告警事件输出。
- Vert.x EventBus 接入适配。
- 运行时配置、生命周期和离线扫描调度。
- 必要的健康检查、指标和错误反馈接口。

### 2.2 本项目不负责

- 设备基础信息管理。
- 原始设备数据的长期存储和查询。
- 业务告警记录的数据库表及生命周期管理。
- 告警确认、派单、处置闭环。
- 用户、组织、角色和权限。
- 页面和管理后台。
- 短信、钉钉、微信等通知实现。
- 复杂报表和大数据离线分析。
- 绑定业务系统已有的数据库表结构。

一句话描述边界：

```text
业务系统负责数据上报、业务存储和通知
AlertFlow 负责实时判断和计算状态
业务系统消费 AlertFlow 输出的告警事件
```

## 3. 数据职责

AlertFlow 可以操作计算所需的运行态数据，但不拥有业务数据。

| 数据类型 | AlertFlow 职责 | 持久化建议 |
| --- | --- | --- |
| 原始设备上报数据 | 消费并计算，不长期保存 | 由业务系统或时序数据库保存 |
| 规则数据 | 通过 `RuleProvider` 读取，不拥有规则业务表 | 来源可为内存、数据库、配置中心或缓存 |
| 滑动窗口 | 负责维护 | 单机使用内存，生产多实例使用 Redis |
| 告警运行状态 | 负责维护触发、持续、恢复状态 | 生产环境应持久化，避免重启后重复告警 |
| 最后上报时间 | 为 OFFLINE 规则维护 | 生产环境应持久化并在实例间共享 |
| 告警事件 | 生成并输出，不作为业务记录长期保存 | 由下游业务系统落库和处理 |
| 设备、用户、组织数据 | 不负责 | 始终由业务系统管理 |

Redis 在本项目中属于**计算状态后端**，不是业务数据库。任何 Redis key、TTL 和序列化格式都应由 AlertFlow 自己管理，且不能要求业务系统迁就 AlertFlow 的内部结构。

## 4. 推荐架构

目标模块结构：

```text
alert-flow
├── alert-flow-core
├── alert-flow-runtime
├── alert-flow-vertx
├── alert-flow-redis
├── alert-flow-standalone       # 独立部署需要时再增加
└── alert-flow-example
```

当前仓库已有 `core`、`runtime`、`vertx`、`redis` 和 `example`。运行时编排职责应继续放在 `runtime`，不要堆积到 `VertxAlarmEngineStarter`。

### 4.1 alert-flow-core

纯 Java 计算内核，不依赖 Vert.x、Spring、Redis、JDBC 或消息中间件。

包括：

```text
AlarmEngine
AlarmRule
DeviceData
AlarmEvent
RuleEvaluator
AlarmState
RuleProvider
WindowStore
AlarmStateStore
LastSeenStore
AlarmEventSink
```

约束：

- 规则判断和状态流转必须可单元测试。
- 基础模型不能包含 Vert.x `JsonObject` 等框架类型。
- 不能直接读取配置文件、数据库或 EventBus。
- 时间、ID 生成等影响确定性测试的能力，后续应允许注入。

### 4.2 alert-flow-runtime

负责组装和运行 `AlarmEngine`，但不关心具体输入协议。

职责：

- Builder 和统一 Options。
- 组装各个 SPI 实现。
- `start()`、`stop()`、`close()` 生命周期。
- OFFLINE 规则周期扫描。
- UPDATED 事件开关和节流策略。
- 运行指标、健康状态和通用错误处理。

### 4.3 alert-flow-vertx

只负责 Vert.x 集成：

- EventBus 输入和输出。
- JSON 与 core 模型的转换。
- 可配置 EventBus 地址。
- 输入校验、错误地址和失败反馈。
- 标准 `Verticle` 或等价部署入口。
- 与 Vert.x 生命周期联动。

不得在 Event Loop 中直接执行 Jedis、JDBC 等阻塞调用。阻塞存储实现必须放入 worker verticle/worker executor；长期方案优先使用异步 SPI 和异步客户端。

### 4.4 alert-flow-redis

只实现 core 定义的状态存储 SPI：

- `RedisWindowStore`
- `RedisAlarmStateStore`
- `RedisLastSeenStore`

多步骤状态操作必须考虑原子性。窗口追加和过期清理、告警状态竞争更新等操作应使用 Lua 或 CAS，避免多实例重复产生 `TRIGGERED`。

### 4.5 alert-flow-standalone

在嵌入能力稳定后再增加，用于把 runtime 和输入输出适配器打包为独立服务。

独立服务不是另一套引擎，只负责：

- 加载配置。
- 选择适配器和状态后端。
- 启停 Runtime。
- 暴露健康检查和指标。
- 提供容器化部署入口。

## 5. 核心流程

```text
设备数据输入
   ↓
协议适配与数据校验
   ↓
AlarmEngine.handle(DeviceData)
   ↓
RuleProvider.findRules(data)
   ↓
WindowStore.appendAndGet(...)
   ↓
RuleEvaluator.evaluate(...)
   ↓
AlarmStateStore 状态流转
   ↓
AlarmEventSink.emit(event)
   ↓
下游业务系统保存、通知或处置
```

离线判断使用独立流程：

```text
设备数据输入 → LastSeenStore 更新最后上报时间
定时任务 → checkOffline(now) → 判断 OFFLINE 规则 → 输出告警事件
新数据到达 → 恢复对应 OFFLINE 告警
```

`last seen` 状态不能长期保留在 `AlarmEngine` 内部 Map 中，否则重启和多实例部署会导致离线检测失忆或不一致。

## 6. 核心模型

### 6.1 DeviceData

建议字段：

```text
dataId          # 后续用于幂等，可选
deviceId
deviceType
metric
value
unit
reportTime
tags
```

输入必须至少校验 `deviceId`、`metric`、`value` 和 `reportTime`。无效数据不得直接导致 EventBus consumer 退出，应产生可观察的失败结果或发送到错误地址。

时间格式优先要求携带时区的 ISO-8601 Instant。若兼容不带时区的时间，默认时区必须由配置指定，不能隐式依赖服务器系统时区。

### 6.2 AlarmRule

关键字段：

```text
ruleId
ruleName
deviceId
deviceType
metric
ruleType
windowSize
operator
threshold
level
enabled
recoverRule
deltaDirection
```

### 6.3 AlarmEvent

关键字段：

```text
eventId
ruleId
ruleName
deviceId
deviceType
metric
level
eventType
triggerValue
windowValue
message
firstTriggerTime
lastTriggerTime
recoverTime
```

事件类型：

- `TRIGGERED`：正常或已恢复状态进入告警。
- `UPDATED`：持续满足告警条件。
- `RECOVERED`：正在告警的状态满足恢复条件。

同一次持续告警必须复用同一个 `eventId`。只有恢复后再次触发，才生成新的 `eventId`。

## 7. 规则和窗口

当前支持：

- `THRESHOLD`：当前值阈值。
- `DELTA`：窗口内变化量。
- `RATE`：窗口内每分钟变化速率。
- `COUNT`：窗口数据点数量。
- `AVG`：窗口平均值。
- `OFFLINE`：一段时间未上报。

窗口 key：

```text
deviceId + metric + ruleId
```

每次数据进入后：

```text
1. 写入窗口
2. 清理过期数据
3. 读取有效窗口
4. 计算规则结果
5. 执行告警状态流转
```

生产环境还必须明确：

- 重复消息的幂等策略。
- 乱序数据是拒绝、容忍还是重新计算。
- 同一 `deviceId + metric` 是否保证串行处理。
- 窗口最大数据量及内存/Redis 保护策略。

在这些策略确定前，不应宣称支持可靠的水平扩展。

## 8. 告警状态和事件策略

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

状态流转：

```text
NORMAL    → 满足触发条件 → ALARMING  → TRIGGERED
ALARMING  → 继续满足条件 → ALARMING  → UPDATED（可配置）
ALARMING  → 满足恢复条件 → RECOVERED → RECOVERED
RECOVERED → 再次满足条件 → ALARMING  → TRIGGERED
```

`UPDATED` 不能默认无限制地产生。Runtime 至少应支持以下一种策略：

- 完全关闭。
- 固定最小发送间隔。
- 仅在窗口值变化达到指定幅度时发送。
- 周期聚合后发送。

告警事件输出需要明确投递语义。EventBus `publish` 适合进程内或集群内的轻量广播，但不能天然替代可靠消息队列。独立服务若要求可靠投递，应使用带确认和重试的 Sink，并根据需要引入 outbox。

## 9. Vert.x 集成约定

默认 EventBus 地址：

```text
输入：device.data.report
输出：alarm.triggered
      alarm.updated
      alarm.recovered
错误：alarm.input.invalid       # 规划
```

所有地址必须允许通过 Options 配置，以支持多个 AlertFlow 实例或不同业务域共存。

推荐接入方式：

```java
AlertFlowRuntime runtime = AlertFlowRuntime.builder()
        .vertx(vertx)
        .options(options)
        .ruleProvider(ruleProvider)
        .windowStore(windowStore)
        .alarmStateStore(alarmStateStore)
        .lastSeenStore(lastSeenStore)
        .eventSink(eventSink)
        .build();

runtime.start();
```

Vert.x 模块后续应提供标准 Verticle：

```java
vertx.deployVerticle(new AlertFlowVerticle(options));
```

Starter/Verticle 必须：

- 允许注入所有 SPI，而不是固定创建内存实现。
- 启动和停止 OFFLINE 扫描任务。
- 注销 EventBus consumer 和定时器。
- 关闭由自身创建且拥有的资源。
- 不关闭由调用方传入的 Vertx、连接池或客户端。
- 将启动失败通过 Future/Promise 返回给调用方。

## 10. SPI 设计原则

核心扩展接口：

```text
RuleProvider
WindowStore
AlarmStateStore
LastSeenStore
AlarmEventSink
```

约束：

- SPI 使用 core 模型，不暴露适配器实现类型。
- 内存实现用于测试、本地开发和明确的单实例场景。
- 生产多实例部署不得默认使用内存状态实现。
- 异步运行时不得在 Event Loop 调用阻塞 SPI。
- 资源所有权必须明确，只有创建资源的一方负责关闭。
- 存储异常不能静默吞掉；必须反馈、记录并计入指标。

当前同步 SPI 便于第一版实现，但接入异步 Redis、Kafka 等组件前，需要评估是引入异步 SPI，还是由 runtime 统一隔离阻塞调用。不要在各适配器中各自实现不同的线程策略。

## 11. 配置与可观测性

Runtime Options 至少应逐步覆盖：

- 输入和输出地址。
- 输入时间格式与默认时区。
- OFFLINE 扫描周期。
- UPDATED 是否输出及节流策略。
- 状态后端类型。
- 错误处理地址或处理器。
- 并发度和 worker 配置。

基础指标建议：

- 输入消息数、无效消息数。
- 规则匹配数和计算耗时。
- 各类型告警事件数。
- 窗口和状态存储失败数。
- Event Sink 成功/失败数。
- 当前告警数量。
- 最后一次 OFFLINE 扫描时间及耗时。

日志不得输出 Redis 密码、消息凭据或完整敏感 tags。

## 12. 开发原则

- `alert-flow-core` 不依赖 Vert.x、Spring、数据库或中间件。
- 新增输入输出协议时放入独立适配模块。
- 业务系统负责保存告警、发送通知、管理设备和规则来源。
- 状态流转、窗口计算和规则判断必须有单元测试。
- 多实例能力以原子状态和幂等为前提，不以“接入 Redis”代替一致性设计。
- 公共 API 优先保持小而稳定，内部实现可以演进。
- 当前保持 Java 8 兼容，不使用 `record`、switch expression、`List.of` 等新语法，除非同步升级构建目标。
- 不为尚未确认的协议提前实现 adapter。
- 文档中的“已支持”和“规划中”必须明确区分。

## 13. 当前实现状态与已知缺口

阶段一、二已经具备以下能力：

- 独立 runtime、Options、Builder、生命周期和 OFFLINE 调度。
- Starter/Verticle 可注入 Window、State、LastSeen、Deduplicator 和 Sink。
- Redis 窗口、告警状态、最后上报及幂等实现。
- 告警状态使用版本 CAS，Redis 使用 Lua 保证原子更新。
- EventBus 输入校验、错误地址、可配置地址和 worker 隔离。
- `dataId` 幂等；早于 LastSeen 的乱序消息整条拒绝，已接受的窗口数据按事件时间排序。
- UPDATED 关闭及最小发送间隔配置。
- core/runtime/Vert.x 的生命周期、并发和校验测试。

仍需在进入阶段三前持续验证：

- Redis 实例故障、网络抖动和大并发压力场景。
- 可靠事件投递；当前状态提交后 Sink 失败不具备 outbox 补偿。
- Redis Cluster 下跨 key Lua 和索引结构的兼容方案；当前 Redis 实现面向单实例/Sentinel。
- 完整运行指标和健康检查端点。

新增功能前应优先判断是否会扩大这些缺口。

## 14. 推荐开发路线

### 阶段一：完善可嵌入能力

1. 新增 `alert-flow-runtime`。
2. 提供 Builder、Options 和完整生命周期。
3. 允许注入 `RuleProvider`、`WindowStore`、`AlarmStateStore`、`LastSeenStore` 和 `AlarmEventSink`。
4. 抽象 `LastSeenStore`，移除 Engine 内部最后上报 Map。
5. 增加 OFFLINE 扫描调度及停止逻辑。
6. 增加输入校验、错误处理和可配置 EventBus 地址。
7. 增加 UPDATED 开关或节流。
8. 完善 Vert.x 集成测试。

### 阶段二：完善生产状态后端

1. 实现 `RedisAlarmStateStore`。
2. 实现 `RedisLastSeenStore`。
3. 定义同一设备指标的串行或原子处理策略。
4. 定义消息幂等键及重复处理策略。
5. 定义乱序数据策略。
6. 避免在 Event Loop 中使用阻塞 Jedis。
7. 使用 Lua/CAS 保证关键状态流转原子性。
8. 增加 Redis 故障、并发和重启恢复测试。

### 阶段三：独立服务和更多适配器

在前两阶段稳定后，根据真实接入需求选择实现：

- Kafka adapter。
- MQTT adapter。
- HTTP ingest adapter。
- 独立服务配置和容器镜像。
- 健康检查、指标和可靠事件投递。

不要同时实现全部协议；每个 adapter 都应由明确的业务场景驱动。

## 15. 暂不实现

当前阶段不要实现：

```text
管理后台
规则配置页面
用户和复杂权限
告警工单闭环
复杂多租户计费
规则 DSL 或脚本引擎
通用流式计算框架
大数据离线分析
复杂报表
通知渠道
绑定业务表结构的数据库迁移脚本
未经需求验证的全套消息协议
```

## 16. 当前目标

近期目标不是继续堆叠规则类型，而是先确保：

1. 状态归属清晰。
2. 重启后计算状态可恢复。
3. 多实例不会重复触发告警。
4. Vert.x Event Loop 不被阻塞。
5. 嵌入方可以通过稳定、可配置的 API 管理完整生命周期。

达到这些目标后，AlertFlow 才能从可运行的预警引擎演进为可靠的告警基础设施服务。
