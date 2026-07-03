---
title: output — 输出与报告
description: baksmali 的 JSON 序列化、聚合视图与变换报告渲染层，统一驱动 list/xref/transform 子命令的机器可读输出。
outline: [2, 3]
---

# 📤 output — 输出与报告

`org.jf.baksmali.output` 是 baksmali 的**输出渲染层**。它把 dexlib2 的内存对象（`Reference`、`ClassDef`）与命令执行结果，统一渲染成扁平、可预测的 JSON 或人类可读文本，供 AI agent、脚本管道与终端用户消费。该包不含 I/O 落盘逻辑——磁盘写入由各 transform 命令通过 `writeResult()` 完成；本包只负责"打印到 stdout 的最后一公里"。

## 🧬 类清单

| 类 | 职责 |
|---|---|
| `JsonOutput` | 把 `Reference`/`ClassDef` 序列化为扁平 `JsonObject`，供 list/xref 子命令产出机器可读数组 |
| `AggregatingOutput` | 渲染 `--count` 与 `--group-by` 聚合视图，支持 text/JSON 双模式 |
| `TransformReport` | 构造 transform 子命令（unlock/replace/strip-debug/patch）成功后的单行结构化报告 |

三者均无状态或仅持有 `OutputFormatArguments` 引用，可独立单测。

## 🗺️ 类间关系

```mermaid
flowchart LR
  subgraph 命令层
    LC[ListClassesCommand]
    LR[ListReferencesCommand]
    UN[UnlockCommand]
    RP[ReplaceCommand]
    SD[StripDebugCommand]
    PA[PatchCommand]
  end
  subgraph output 包
    JO[JsonOutput]
    AO[AggregatingOutput]
    TR[TransformReport]
  end
  OFA[OutputFormatArguments<br/>--format json|text]
  LC --> JO
  LR --> JO
  LC -. --count/--group-by .-> AO
  AO --> OFA
  UN --> TR
  RP --> TR
  SD --> TR
  PA --> TR
  TR --> OFA
  TR -->|stdout 一行| STDOUT[(stdout)]
  JO -->|stdout| STDOUT
  AO -->|stdout| STDOUT
```

## ⚡ 典型协作流程

**list 类命令**（如 `list classes`）：遍历 `DexBackedClassDef`，对每个类调 `JsonOutput.toJson(ClassDef)` 得 `JsonObject`，攒成 `List` 后用 `toJsonArray()` 一次打印。当带 `--count` 时改走 `AggregatingOutput.renderCount(count)`，带 `--group-by class` 时走 `renderGroupedBy(items, keyExtractor, null)`。

**transform 类命令**：先 `AccessFlagTransform`/`StringReplaceTransform` 等改写 dex，`writeResult()` 落盘，再 `TransformReport.base(command, input, output)` 种入公共字段，命令追加自身字段（如 `publicized`、`matched`、`rules`），最后 `DexTransformCommand.emitReport()` 调 `TransformReport.render()` 选 JSON 或文本。

## 📊 源码要点

- **扁平 schema 约定**：`JsonOutput` 注释明示"intentionally flat and predictable"，方法引用即 `{"class","name","parameters","returnType"}`（`JsonOutput.java:55-59`）。`disableHtmlEscaping()` 保证 URL/正则元字符不被转义（`JsonOutput.java:66`）。
- **引用分派**：`JsonOutput.toJson(Reference)` 按 `MethodReference`/`FieldReference`/`StringReference`/`TypeReference` instanceof 分派，未识别类型回退到单字段 `{"value":...}`（`JsonOutput.java:73-88`）。
- **ClassDef 全量序列化**：`toJson(ClassDef)` 输出 `type/superclass/accessFlags/interfaces/fields/methods`，方法含 `parameters`/`returnType`/`accessFlags`（`JsonOutput.java:131-169`）。
- **聚合双模式**：`renderCount` JSON 输出 `{"count":N}`，文本仅打印数字（`AggregatingOutput.java:64-72`）；`renderGroupBy` JSON 输出 `[{group,count}]` 数组，文本输出 `count\tgroup`（`AggregatingOutput.java:79-94`）。
- **报告不可漂移**：`TransformReport.render(boolean, JsonObject, String)` 从同一调用同时产出 JSON 与人读句，"two modes never drift apart"（`TransformReport.java:46-47,74-81`）。
- **base 种子**：`TransformReport.base("unlock", input, output)` 统一注入 `command/input/output` 三字段（`TransformReport.java:60-67`）。
- **emitReport 入口**：`DexTransformCommand.emitReport()` 调 `TransformReport.render(isJson(), report, humanText)` 后 `println`（`DexTransformCommand.java:87-88`）。

## 🛠️ 真实命令 → 输出示例

```bash
# list classes — JsonOutput.toJson(ClassDef) 数组
java -jar baksmali.jar list classes LocalTest/classes.dex
```
```json
[{"type":"LLocalTest;","superclass":"Ljava/lang/Object;","accessFlags":1,"interfaces":[],"fields":[],"methods":[{"name":"method1","parameters":[],"returnType":"V","accessFlags":9}]}]
```

```bash
# 聚合 — AggregatingOutput.renderCount / renderGroupBy
java -jar baksmali.jar l m --count accessorTest.dex
java -jar baksmali.jar l m --group-by class accessorTest.dex
```
```json
{"count":432}
[{"group":"Ljava/lang/Object;","count":1},{"group":"Lorg/jf/dexlib2/AccessorTypes$Accessors;","count":232}]
```

```bash
# transform — TransformReport.base + 命令字段
baksmali unlock app.apk -o unlocked.dex
baksmali strip-debug app.apk -o stripped.dex
baksmali patch app.apk --method 'isPremium' --return true -o patched.dex
```
```json
{"command":"unlock","input":"app.apk","output":"unlocked.dex","publicized":true,"definalized":true}
{"command":"strip-debug","input":"app.apk","output":"stripped.dex","strippedDebugInfo":true}
{"command":"patch","input":"app.apk","output":"patched.dex","matched":1,"return":"true","methodFilter":"isPremium"}
```

> `--format text` 时上述 JSON 行退化为 `Wrote unlocked.dex (publicized, definalized).` 句式，二者同源。

## 🔎 共享参数（`OutputFormatArguments`）

list/xref/search 子命令通过 `@ParametersDelegate` 注入下表选项，由 `AggregatingOutput`/`TransformReport` 读取 `isJson()` 切换模式：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `--format` | `json` | `json`（机器可读，默认）/ `text`（人类可读）。`OutputFormatArguments.java:52-54` |

transform 子命令共享 `DexTransformCommand.outputFormatArguments`（`DexTransformCommand.java:64`），故 unlock/replace/strip-debug/patch 同样支持 `--format text`。

## 延伸阅读

- [CLI 总览：list](../../cli/list.md)
- [CLI 总览：transform](../../cli/transform.md)
- [DexTransformCommand — 变换命令基类](../dexlib2/writer.md)
- [baksmali 命令索引](../../cli/)
