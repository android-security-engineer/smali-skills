---
title: baksmali list types
description: 列举 dex 文件类型表（type table）中的全部类型 ID，默认输出 JSON。
outline: [2, 3]
---

# 🛠️ baksmali list types

`baksmali list types`（别名 `l t` / `type` / `t`）列举一个 dex 文件 **type id 表** 中的全部类型引用——即类型描述符（如 `I`、`Ljava/lang/String;`、`[B`、`[[I`）。这是最轻量的「这个 dex 提到哪些类型」视图，无需完整反汇编，常用于依赖面盘点、混淆类型侦查、加固分析的前置侦察。

类型表是 dex 共享去重池：每个不重复的类型描述符只占一个 type id，被字段表/方法原型/注解/类定义等共同引用。因此 `list types` 输出的去重集合正是该 dex 的「类型词汇表」。

## 🔎 命令定位

| 属性 | 值 |
|---|---|
| 全名 | `baksmali list types` |
| 别名 | `baksmali l t`、`baksmali list type`、`baksmali l type` |
| 父命令 | `list`（`ListCommand`，`l`） |
| 命令类 | `org.jf.baksmali.ListTypesCommand` |
| 直接父类 | `org.jf.baksmali.ListReferencesCommand`（通用列举/聚合逻辑） |
| 继承链根 | `DexInputCommand` → `Command` |
| 引用类型 | `ReferenceType.TYPE`（= `1`，见 `dexlib2/.../ReferenceType.java:39`） |
| 输出 | **默认 JSON**；`--format text` 切换人读文本 |

`ListTypesCommand` 本身极薄——构造时把 `ReferenceType.TYPE` 传给父类，所有参数与主流程都来自 `ListReferencesCommand`（见 `baksmali/src/main/java/org/jf/baksmali/ListReferencesCommand.java:49`）。`commandName = "types"`、`commandAliases = { "type", "t" }` 见 `ListTypesCommand.java:44`。

## 📊 参数表

参数来自三层：自身注解、`DexInputCommand`（输入文件）、`@ParametersDelegate` 委托的两个参数组。

| 参数 | 来源 | 说明 | 默认 | 必填 |
|---|---|---|---|---|
| `file`（位置参数） | `DexInputCommand.java:61` | dex/apk/oat/odex 文件；apk/oat 多 dex 时可用 `app.apk/classes2.dex` 指定条目 | — | 是 |
| `-a`, `--api` | `DexInputCommand.java:56` | 文件目标 API level（影响 opcode 集） | `-1`（自动探测） | 否 |
| `--format` | `OutputFormatArguments.java:52` | `json`（默认，机器/Agent 友好）或 `text`（人读） | `json` | 否 |
| `--count` | `ListAggregationArguments.java:56` | 仅输出总数，不输出条目 | `false` | 否 |
| `--group-by` | `ListAggregationArguments.java:60` | 按键分桶计数；仅 `class`，且**仅对 method/field 有效**，对 types 会被忽略并告警 | 无 | 否 |
| `-h`, `-?`, `--help` | `ListReferencesCommand.java:53` | 显示用法 | `false` | 否 |

> 对 types 而言 `--group-by class` 无意义（类型本身没有 defining class），父类会打印 `--group-by class only applies to methods and fields; ignoring.`（见 `ListReferencesCommand.java:98`）。

## 🧬 命令主流程

```mermaid
flowchart TD
    A[run: 校验 inputList] -->|空或 --help| U[usage 退出]
    A -->|多于 1 个文件| E[报错 Too many files]
    A -->|单个输入| L[loadDexFile]
    L --> M[遍历 dexFile.getReferences TYPE<br/>物化为 List]
    M --> C{--count?}
    C -->|是| CC[AggregatingOutput.renderCount<br/>输出 {"count":N}]
    C -->|否| G{--group-by class?}
    G -->|是 且 非 method/field| W[告警并忽略]
    G -->|否 / 忽略后| F{--format json?}
    F -->|是| J[JsonOutput.toJson 每个 Reference<br/>输出 JSON 数组]
    F -->|text| T[BaksmaliFormatter.getReference<br/>逐行打印描述符]
    J --> END[stdout]
    T --> END
    CC --> END
```

物化（`ListReferencesCommand.java:84`）先一次性收集全部 `Reference`，使得计数/分组/格式化共用同一份快照，避免重复遍历 dex 字节缓冲。对 TYPE 引用，`JsonOutput` 序列化为 `{"type":"<descriptor>"}`，`BaksmaliFormatter` 输出裸描述符。

## ⚡ 真实命令与输出

### 默认 JSON

```bash
java -jar baksmali.jar list types baksmali/src/test/resources/LocalTest/classes.dex
```

实际输出（来自 `skills/dex-list-classes` 真实实跑）：

```json
[{"type":"I"},{"type":"J"},{"type":"LAnnotationWithValues;"},{"type":"LLocalTest;"},{"type":"Ljava/lang/Object;"},{"type":"Ljava/lang/String;"},{"type":"V"}]
```

### 人读文本

```bash
java -jar baksmali.jar l t app.apk --format text
```

文本对照（节选，含基本类型、类类型、数组）：

```
I
Z
Ljava/lang/String;
Lcom/example/Main;
[B
[[I
```

### 计数模式

```bash
java -jar baksmali.jar l t app.apk --count
```

输出 `{"count":7}`（与上例 LocalTest 的 7 个类型 ID 一致）。

## 🗺️ 用法要点

- **去重性质**：类型表已去重，输出条数 = 该 dex type id 表大小，而非引用点数。要看「谁引用了某类型」用 `baksmali xref type-refs`。
- **多 dex 容器**：apk/oat 含多个 dex 时，不带条目后缀默认取 `classes.dex`（`DexInputCommand.java:160`）；指定条目用 `app.apk/classes2.dex`，引号包裹可精确匹配。
- **基本类型与数组**：`I`/`J`/`Z`/`B` 等基本类型及 `[B`、`[[I` 数组也各占一个 type id，会一并列出——这是判断 dex 是否触及多维数组或原生类型的快速途径。
- **Agent 友好**：默认 JSON 利于 `jq` 过滤；如筛出所有自定义类 `jq '.[] | select(.type|test("^Lcom/"))'`。

## 📤 源码要点

- `ListTypesCommand.java:42` — `@Parameters(commandDescription = "Lists the type ids in a dex file's type table.")`
- `ListTypesCommand.java:44` — `commandName = "types"`，`commandAliases = { "type", "t" }`
- `ListTypesCommand.java:48` — `super(commandAncestors, ReferenceType.TYPE)` 注入引用类型
- `ListReferencesCommand.java:85` — `dexFile.getReferences(referenceType)` 遍历物化
- `ListReferencesCommand.java:106` / `:116` — JSON 与 text 两条输出分支
- `ReferenceType.java:39` — `TYPE = 1` 常量定义

## 延伸阅读

- [baksmali list 命令总览](../../../cli/list.md)
- [Skills: dex-list-classes（含 list types 真实示例）](../../../skills/#列举类型)
- [baksmali xref type-refs（谁引用了某类型）](../commands/xref.md)
- DexInputCommand：多 dex/条目语法
