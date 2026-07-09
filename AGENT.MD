# AGENT.md

## 项目定位

本项目是一个轻量级设备数据实时预警引擎，暂定名为 `alarm-engine`。

它以 jar 包形式被业务系统集成，核心职责是：

* 接收设备上报数据
* 匹配预警规则
* 维护时间窗口
* 判断是否触发或恢复预警
* 输出预警事件

本项目不直接负责设备管理、页面展示、短信通知、权限管理等业务功能。

---

## 核心边界

### 本项目负责

* 预警规则模型定义
* 设备数据输入模型定义
* 时间窗口计算
* 规则判断
* 预警状态去重
* 预警恢复判断
* 预警事件输出
* Vert.x EventBus 接入适配

### 本项目不负责

* 设备基础信息管理
* 原始设备数据长期存储
* 预警记录数据库表设计
* 用户、角色、权限
* 页面管理后台
* 短信、钉钉、微信等通知实现
* 复杂报表统计
* 大数据离线分析

---

## 第一版技术方向

第一版以简单、可嵌入、可测试为主。

入口优先使用 Vert.x EventBus：

```text
device.data.report
```

输出事件：

```text
alarm.triggered
alarm.recovered
alarm.updated
```

后续可以扩展：

```text
Kafka
MQTT
RabbitMQ
HTTP
Spring Event
```

但第一版不要提前实现。

---

## 推荐模块结构

```text
alarm-engine
├── alarm-engine-core
├── alarm-engine-vertx
└── alarm-engine-example
```

### alarm-engine-core

只放核心模型和判断逻辑，不依赖 Vert.x。

包括：

```text
AlarmEngine
AlarmRule
DeviceData
AlarmEvent
WindowBuffer
RuleEvaluator
AlarmStateStore
RuleProvider
AlarmEventSink
```

### alarm-engine-vertx

只负责 Vert.x 适配。

包括：

```text
EventBusDeviceDataConsumer
EventBusAlarmEventPublisher
VertxAlarmEngineStarter
```

### alarm-engine-example

简单演示如何接入和测试。

---

## 核心流程

```text
设备数据上报
   ↓
EventBus Consumer
   ↓
AlarmEngine.handle(DeviceData)
   ↓
规则匹配
   ↓
窗口数据更新
   ↓
规则判断
   ↓
预警状态判断
   ↓
输出预警事件
```

---

## 核心模型

### DeviceData

设备上报数据。

字段建议：

```text
deviceId
deviceType
metric
value
unit
reportTime
tags
```

### AlarmRule

预警规则。

字段建议：

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
```

### AlarmEvent

预警事件。

字段建议：

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

---

## 第一版支持的规则类型

### THRESHOLD

当前值阈值。

示例：

```text
温度 > 80℃
压力 < 0.2MPa
```

### DELTA

窗口变化量。

示例：

```text
5分钟位移变化 >= 10mm
20分钟水位上涨 >= 0.5m
```

### RATE

变化速率。

示例：

```text
温度上升 >= 2℃/min
水位上涨 >= 0.1m/min
```

### COUNT

窗口内满足条件的次数。

示例：

```text
10分钟内超限次数 >= 5
```

### AVG

窗口平均值。

示例：

```text
5分钟平均温度 > 75℃
```

### OFFLINE

离线判断。

示例：

```text
10分钟未上报
```

---

## 时间窗口设计

第一版优先支持滑动窗口。

窗口 key：

```text
deviceId + metric + ruleId
```

窗口内保存最近一段时间的数据点。

数据点结构：

```text
value
reportTime
```

每次新数据进入后：

```text
1. 写入窗口
2. 清理过期数据
3. 根据规则计算窗口值
4. 判断是否触发或恢复
```

DELTA 规则计算：

```text
当前值 - 窗口起点值
```

支持方向：

```text
INCREASE
DECREASE
ABS
```

第一版可默认使用：

```text
ABS
```

---

## 预警状态设计

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
NORMAL → 满足触发条件 → ALARMING
ALARMING → 继续满足条件 → 更新预警
ALARMING → 满足恢复条件 → RECOVERED
RECOVERED → 再次满足条件 → ALARMING
```

注意：

不要每次超限都生成新预警。

---

## 数据库策略

第一版不强依赖数据库。

核心接口：

```text
RuleProvider
AlarmStateStore
AlarmEventSink
```

第一版实现：

```text
MemoryRuleProvider
MemoryAlarmStateStore
EventBusAlarmEventSink
```

后续可扩展：

```text
JdbcRuleProvider
RedisAlarmStateStore
KafkaAlarmEventSink
```

jar 包不要直接绑定业务系统表结构。

---

## 设计原则

* core 模块不要依赖 Vert.x、Spring、数据库
* 入口和出口通过适配器实现
* 规则判断逻辑必须可单元测试
* 时间窗口计算逻辑必须独立
* 不要把通知、页面、权限放进本项目
* 不要为了未来扩展过度设计
* 第一版以内存实现为主
* 接口先稳定，存储后扩展

---

## 不要做的事

第一版不要实现：

```text
管理后台
动态规则配置页面
复杂权限
多租户
分布式一致性
规则 DSL
复杂脚本引擎
流式计算框架
大数据分析
复杂报表
通知渠道
数据库迁移脚本
```

这些功能等核心预警能力稳定后再做。

---

## 推荐开发顺序

```text
1. 定义 DeviceData、AlarmRule、AlarmEvent
2. 定义 RuleType、Operator、AlarmState
3. 实现 THRESHOLD 判断
4. 实现 WindowBuffer
5. 实现 DELTA 判断
6. 实现 AlarmStateStore
7. 实现预警去重
8. 实现恢复逻辑
9. 接入 Vert.x EventBus
10. 编写 example 示例
```

---

## 示例事件地址

输入：

```text
device.data.report
```

输出：

```text
alarm.triggered
alarm.recovered
alarm.updated
```

示例输入：

```json
{
  "deviceId": "D001",
  "deviceType": "displacement_sensor",
  "metric": "displacement",
  "value": 120.5,
  "unit": "mm",
  "reportTime": "2026-07-09T10:00:00"
}
```

示例规则：

```json
{
  "ruleId": "R001",
  "ruleName": "5分钟位移变化预警",
  "deviceType": "displacement_sensor",
  "metric": "displacement",
  "ruleType": "DELTA",
  "windowSize": "PT5M",
  "operator": ">=",
  "threshold": 10,
  "level": "HIGH",
  "enabled": true
}
```

---

## 目标

第一版目标不是做一个完整监控平台，而是做一个稳定、清晰、可嵌入的实时预警引擎。

最终它应该可以这样被使用：

```text
业务系统负责上报数据
alarm-engine 负责判断
业务系统负责保存和通知
```
