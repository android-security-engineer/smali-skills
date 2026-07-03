---
title: baksmali xref callers
description: 反向查询谁调用了指定目标方法，输出每个调用点的方法与字节偏移。
outline: [2, 3]
---

# 🔎 baksmali xref callers

`baksmali xref` 子命令组的一员，专门做**方法引用**的反向交叉引用：给定一个目标方法描述符，列出 dex 中所有 `invoke-*` 它的位置（调用方法 + 指令偏移）。是正向 `list methods` 的补充——`list` 告诉你"有哪些方法"，`xref callers` 告诉你"谁在调它"。

## 🛠️ 命令定位

- 命令组：`xref`（父类 `XrefCommand` 注册三个子命令：`callers` / `field-refs` / `type-refs`）
- 子命令名：`callers`
- 别名：`caller`、`c`
- 描述（`@Parameters(commandDescription)`）：`Find methods that call a given target method.`
- 继承链：`XrefCallersCommand` → `XrefTargetCommand` → `DexInputCommand` → `Command`
- 引用类型过滤：`referenceClass()` 返回 `MethodReference.class`，只报告 `invoke-*` 类引用

源码：`baksmali/src/main/java/org/jf/baksmali/XrefCallersCommand.java:50-62`；子命令注册见 `XrefCommand.java:73`。

## 📊 参数

`XrefCallersCommand` 自身无独有 `@Parameter`，全部来自父类 `XrefTargetCommand` 与 `DexInputCommand`：

| 参数 | 说明 | 默认 | 必填 | arity | 来源 |
| --- | --- | --- | --- | --- | --- |
| `--target` | 要查找的目标方法描述符；先精确匹配，再子串回退。省略则列出所有方法目标的引用点 | 无（列出全部） | 否 | 单值 | `XrefTargetCommand.java:78-81` |
| `--format` | 输出格式：`json`（默认，机器可读）或 `text`（人读）；未识别值回退 JSON | `json` | 否 | 单值 | `OutputFormatArguments.java:52-54` |
| `-a`, `--api` | dex 文件的数字 API level，用于选择 opcode 集 | `-1`（自动） | 否 | 单值 | `DexInputCommand.java:56-59` |
| `-h`, `-?`, `--help` | 显示用法信息 | false | 否 | 布尔 | `XrefTargetCommand.java:66-68` |
| `file`（位置参数） | dex/apk/oat/odex 文件；多 dex 容器可用 `app.apk/classes2.dex` 路径语法 | — | 是 | 列表（取首项） | `DexInputCommand.java:61-65` |

注意：位置参数虽为 `List<String>`，但 `run()` 在 `XrefTargetCommand.java:100-104` 校验 `inputList.size() > 1` 即报 `Too many files specified`，**仅接受单个 dex**。本命令**没有** `--boot-class-path`、`--deodex` 等分析参数——它只索引原始字节码里的引用指令，不做类型解析。

## 🧬 主流程

```mermaid
flowchart TD
    A["run()<br/>XrefTargetCommand:94"] --> B{help 或<br/>inputList 为空?}
    B -- 是 --> Z["usage() 退出"]
    B -- 否 --> C{inputList.size()>1?}
    C -- 是 --> Z
    C -- 否 --> D["loadDexFile(input)<br/>DexInputCommand:111"]
    D --> E["ReferenceFinder.index(classes)<br/>ReferenceFinder:88"]
    E --> F["遍历 getTargets()<br/>按 MethodReference 过滤"]
    F --> G{target!=null &&<br/>!matches(key,target)?}
    G -- 是 --> F
    G -- 否 --> H["加入 matchedTargets"]
    H --> I{matchedTargets<br/>为空?}
    I -- 是 --> J["输出 [] / stderr 提示"]
    I -- 否 --> K{--format json?}
    K -- 是 --> L["renderJson:<br/>target + sites[]"]
    K -- 否 --> M["renderText:<br/>target + 缩进 caller @ offset"]

    style E fill:#fff3e0
    style L fill:#e3f2fd
    style M fill:#e8f5e9
```

索引阶段（`ReferenceFinder.java:88-99`）遍历每个类 → 方法 → 指令，遇到 `ReferenceInstruction` 即用 `BaksmaliFormatter` 格式化出引用键（如 `Lcom/Example;->foo()V`），连同调用方法描述符和 `codeUnits` 偏移记入 `ReferenceSite`（`ReferenceFinder.java:62-72`）。

匹配规则（`XrefTargetCommand.java:155-157`）：`key.equals(target) || key.contains(target)`——精确匹配优先，失败则子串回退，因此 `foo()V`、`->track(` 都能命中。

## ⚡ 典型用法

```bash
# 默认就是 JSON；下面不再重复 --format json
java -jar baksmali.jar xref callers app.apk --target "Lcom/Example;->foo()V"
# 人读文本
java -jar baksmali.jar xref callers app.apk --target "Lcom/Example;->foo()V" --format text
# 子串匹配（不记得完整签名时）—— foo()V 会匹配任何含该子串的方法
java -jar baksmali.jar xref callers app.apk --target "foo()V"
# 不指定 --target：列出所有方法目标及其引用点
java -jar baksmali.jar xref callers app.apk
```

## 📤 真实命令 → 输出示例

用仓库自带的 `accessorTest.dex` fixture——`access$072` 是内部类访问外部类私有成员时编译器生成的合成桥接方法：

```bash
java -jar baksmali.jar xref callers \
  dexlib2/src/test/resources/accessorTest.dex \
  --target "Lorg/jf/dexlib2/AccessorTypes;->access\$072(Lorg/jf/dexlib2/AccessorTypes;I)Z"
```

默认 JSON 输出：

```json
[{"target":"Lorg/jf/dexlib2/AccessorTypes;->access$072(Lorg/jf/dexlib2/AccessorTypes;I)Z","sites":[{"caller":"Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_and(Z)V","offset":"0x2"}]}]
```

人读文本对照（`--format text`，渲染逻辑见 `XrefTargetCommand.java:159-167`）：

```
Lorg/jf/dexlib2/AccessorTypes;->access$072(Lorg/jf/dexlib2/AccessorTypes;I)Z
  Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_and(Z)V @ offset 0x2
```

`boolean_and(Z)V` 在偏移 `0x2` 处 `invoke-static` 了 `access$072`——`offset` 是引用指令在方法体内的字节偏移（hex，`codeUnits` 累加见 `ReferenceFinder.java:114`），可用于定位到反汇编输出中的具体行。无匹配时 JSON 输出 `[]`，文本模式向 stderr 报 `No references found matching: <target>`（`XrefTargetCommand.java:133-141`）。

JSON 由 `renderJson`（`XrefTargetCommand.java:169-190`）构造：每个目标一个对象，含 `target` 字符串与 `sites` 数组；每个 site 含 `caller`（调用方法描述符）与 `offset`（hex 字符串）。

## 🗺️ 典型场景

| 场景 | 命令 |
| --- | --- |
| 找某 Activity 的所有启动点 | `xref callers --target "->startActivity(...)"` |
| 找构造函数的所有调用者 | `xref callers --target "-><init>()V"` |
| 找某 SDK 方法的集成点 | `xref callers --target "Lcom/sdk/;->track("` |
| 不带 `--target`，dump 全部方法调用关系 | `xref callers app.apk` |

## 延伸阅读

- ../xref-fieldrefs.md — 字段引用反向查询（`iget/iput/sget/sput`）
- ../xref-typerefs.md — 类型引用反向查询（`check-cast/new-instance` 等）
- [../../../cli/xref.md](../../../cli/xref.md) — `xref` 命令组用户向导与场景速查
- [../../../cli/list.md](../../../cli/list.md) — 正向列举命令，与 `xref` 互补
- ../../../../skills/dex-xref — `dex-xref` 技能：Agent 调用 xref 的工作流
