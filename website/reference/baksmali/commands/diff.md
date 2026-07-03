---
title: baksmali diff
description: 在 opcode 层面语义对比两个 dex/apk，报告新增/删除/改动的类与方法，退出码可脚本门控。
outline: [2, 3]
---

# 🧬 baksmali diff

`baksmali diff OLD NEW` 在**操作码（opcode）层面**对两个 dex/apk 做语义差异比对：报告 NEW 独有的类、OLD 独有的类，以及对两边都存在的类，其新增/删除/改动的方法。方法被判为「改动」的依据是 **opcode 序列不同**——寄存器分配、调试信息（`.line`/`.local`/参数名）、指令偏移都被**刻意忽略**，所以「同源重编译产生的噪声」不会被误报为语义改动。这正是它区别于 `diff <(baksmali d a) <(baksmali d b)` 纯文本对比的地方。

## 🛠️ 命令定位

- 命令名：`diff`（`@ExtendedParameters(commandName="diff")`），无别名
- 描述（`@Parameters(commandDescription)`）：`Semantic (opcode-level) diff of two dex/apk files: added/removed/changed classes and methods.`
- 继承链：`DiffCommand` → `DexInputCommand` → `Command`（不依赖 `XrefCommand` / `ListCommand`）
- 比对引擎：委托 `org.jf.baksmali.diff.DexDiff`（纯模型、无 I/O）计算差异并导出文本/JSON
- 输入：恰好两个位置参数 `OLD` `NEW`，顺序敏感；支持多 dex 条目语法 `app.apk/classes2.dex`

源码：`baksmali/src/main/java/org/jf/baksmali/DiffCommand.java:64-107`；引擎在 `baksmali/src/main/java/org/jf/baksmali/diff/DexDiff.java:65-283`。

## 📊 参数

`DiffCommand` 自有 `@Parameter` 极少——仅 `--help`；格式控制委托 `OutputFormatArguments`，dex 加载与位置参数继承自 `DexInputCommand`：

| 参数 | 说明 | 默认 | 必填 | arity | 来源 |
| --- | --- | --- | --- | --- | --- |
| `OLD` `NEW`（位置参数） | 两个 dex/apk/oat/odex 文件，**顺序为 OLD→NEW**；多 dex 容器可用 `app.apk/classes2.dex` 路径语法 | — | 是 | 列表（恰好 2 项） | `DexInputCommand.java:61-65` |
| `--format` | 输出格式：`json`（默认，机器可读）或 `text`（人读）；未识别值回退 JSON | `json` | 否 | 单值 | `OutputFormatArguments.java:52-54` |
| `-a`, `--api` | dex 文件的数字 API level，用于选择 opcode 集 | `-1`（自动） | 否 | 单值 | `DexInputCommand.java:56-59` |
| `-h`, `-?`, `--help` | 显示用法信息 | false | 否 | 布尔 | `DiffCommand.java:66-68` |

参数校验（`DiffCommand.java:78-86`）：`inputList` 为 `null` 或长度 `!= 2` 即走 `usage()` 退出；恰为 1 项时报 `Two dex/apk files are required: OLD and NEW.`，超过 2 项报 `Too many files specified; exactly two are required: OLD and NEW.`。本命令**没有** `--boot-class-path`、`--deodex` 等分析参数——它只做原始字节码比对，不做类型解析或 deodex。

## 🧬 主流程

```mermaid
flowchart TD
    A["run()<br/>DiffCommand:77"] --> B{help 或<br/>inputList.size()!=2?}
    B -- 是 --> Z["usage() 退出"]
    B -- 否 --> C["loadDexFile(inputList.get(0))<br/>DexInputCommand:111 → oldDex"]
    C --> D["loadDexFile(inputList.get(1))<br/>第二次独立加载 → newDex"]
    D --> E["DexDiff.compute(oldDex, newDex)<br/>DexDiff.java:93-123"]
    E --> F{--format json?<br/>OutputFormatArguments}
    F -- 是 --> G["diff.toJson()<br/>DexDiff.java:257-273"]
    F -- 否 --> H["diff.toText()<br/>DexDiff.java:225-249"]
    G --> I{diff.isEmpty()?}
    H --> I
    I -- 否 --> J["System.exit(1)"]
    I -- 是 --> K["（隐式退出码 0）"]

    style E fill:#fff3e0
    style G fill:#e3f2fd
    style H fill:#e8f5e9
    style J fill:#fce4ec
```

比对算法（`DexDiff.java:93-123`）：先把两个 dex 的类按类型描述符装入 `TreeMap`（`byType`，`DexDiff.java:152-158`，天然字典序）；遍历 NEW 键集求 `addedClasses`、遍历 OLD 键集求 `removedClasses`；对两边都有的类调用 `compareMethods`（`DexDiff.java:126-149`），按方法规范描述符 `Lcls;->name(params)ret`（`descriptor`，`DexDiff.java:190-198`）比对 **opcode 签名**（`opcodeSignature`，`DexDiff.java:174-187`：逗号拼接的 opcode 名序列，无方法体的 abstract/native 记为空串）。NEW 独有→`addedMethods`，OLD 独有→`removedMethods`，签名不等→`changedMethods`。所有集合均为 `TreeMap`/`TreeSet` 派生，输出**确定性**。

## ⚡ 典型用法

```bash
# 默认就是 JSON；两个位置参数 OLD NEW
java -jar baksmali.jar diff old.apk new.apk
# 人读文本
java -jar baksmali.jar diff old.apk new.apk --format text
# 多 dex 条目语法：定向对比某个 classes2.dex
java -jar baksmali.jar diff app.apk/classes2.dex app2.apk/classes2.dex
# 退出码门控：仅当语义一致才继续
java -jar baksmali.jar diff orig.dex patched.dex && echo "未改动" || echo "有差异，请复核"
```

## 📤 真实命令 → 输出示例

用仓库自带的两个不重叠 fixture dex 对比——`accessorTest.dex`（含 `AccessorTypes` 两个类）与 `LocalTest/classes.dex`（含 `LLocalTest;` 一个类），类集合完全不重叠，因此 `changedClasses` 为空：

```bash
java -jar baksmali.jar diff \
  dexlib2/src/test/resources/accessorTest.dex \
  baksmali/src/test/resources/LocalTest/classes.dex
```

默认 JSON 输出（`DexDiff.toJson()`，`DexDiff.java:257-273`）：

```json
{
  "addedClasses": ["LLocalTest;"],
  "removedClasses": [
    "Lorg/jf/dexlib2/AccessorTypes$Accessors;",
    "Lorg/jf/dexlib2/AccessorTypes;"
  ],
  "changedClasses": []
}
```

`addedClasses` 是 NEW 独有的类，`removedClasses` 是 OLD 独有的类，`changedClasses` 为空（两边没有同名类，自然没有「同名但方法体变化」）。退出码 `1`。

人读文本对照（`--format text`，`DexDiff.toText()`，`DexDiff.java:225-249`）：

```
+ class LLocalTest;
- class Lorg/jf/dexlib2/AccessorTypes$Accessors;
- class Lorg/jf/dexlib2/AccessorTypes;
```

无差异时文本输出 `No semantic differences.`（`DexDiff.java:226-228`），JSON 输出三个空数组，退出码 `0`。文本报告中 `~ class` 段落仅在 `changedClasses` 非空时出现，下挂缩进的 `+`/`-`/`~` 方法行。

## 🗺️ 典型场景

| 场景 | 命令 |
| --- | --- |
| 验证补丁精确性 | `diff app.apk patched.dex`（应只显示目标方法一处 `~`） |
| 版本升级审计 | `diff v1.apk v2.apk \| jq '.addedClasses'` |
| 恶意样本比对 | 判断「重打包」样本相对原版改动了哪些方法体 |
| 回归基线 | `diff baseline.dex build.dex` 作 CI 门，退出码 `1` 即 fail |
| 多 dex 定向 | `diff app.apk/classes2.dex app2.apk/classes2.dex` 只看某一 dex |

## 延伸阅读

- [../../../cli/diff.md](../../../cli/diff.md) — `diff` 命令用户向导与场景速查
- [./disassemble.md](./disassemble.md)（如已生成） — 反汇编命令，可配合做逐方法文本复核
- [./search.md](./search.md) — 指令模式搜索，定位 diff 命中的具体调用点
- ../../../../skills/dex-diff — `dex-diff` 技能：Agent 调用 diff 的工作流
- ../../../../skills/dex-roundtrip — 往返一致性验证（与 diff 互补）
