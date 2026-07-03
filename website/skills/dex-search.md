---
title: dex-search — 指令模式搜索
description: 在方法指令流中按连续 opcode 序列与通配符正向搜索字节码模式，支持类/方法正则过滤，默认 JSON 输出。
outline: [2, 3]
---

# 🔍 dex-search — 指令模式搜索

`dex-search` 是 smali-skills 的**正向搜索**工具：在方法指令流中匹配连续的 opcode 模式（如 `const-string,invoke-virtual`），支持 `*` 通配符和 `--class`/`--method` 正则过滤。适合定位「加载字符串后立即调用」「new-instance 后调用构造函数」「反射 `invoke` 链」等字节码模式，输出 `caller + offset + 指令` 三元组。

## 🧭 能力与工作流

```mermaid
flowchart LR
    DEX[("dex/apk/odex/oat")] --> FAC["DexFileFactory<br/>自动检测格式"]
    FAC --> CL["遍历 ClassDef"]
    FLT["--class 正则<br/>作用于类型描述符"] -.->|过滤| CL
    CL --> MD["遍历 Method"]
    MFLT["--method 正则<br/>作用于方法名"] -.->|过滤| MD
    MD --> IM["getImplementation()<br/>指令流"]
    IM --> WIN["滑动窗口子序列匹配<br/>PatternSearcher"]
    PAT["--opcode 序列<br/>const-string,*,invoke-virtual"] --> WIN
    WIN -->|"命中"| HIT["Match: caller + offset + instructions"]
    HIT --> JSON[("默认 JSON<br/>面向 Agent/脚本")]
    HIT --> TEXT["--format text<br/>人读文本")]

    style FAC fill:#fff3e0
    style WIN fill:#e3f2fd
    style HIT fill:#e8f5e9
```

核心契约见 `baksmali/src/main/java/org/jf/baksmali/PatternSearcher.java:108`（`search` 遍历 `ClassDef → Method → Instruction`）与 `:127`（`searchMethod` 做滑动窗口匹配）。

## 📦 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 🚀 快速开始

```bash
# 单 opcode
java -jar baksmali.jar search app.apk --opcode invoke-virtual

# opcode 序列（逗号分隔，按顺序连续匹配）
java -jar baksmali.jar search app.apk --opcode const-string,invoke-virtual

# 通配符 *：匹配任意单条 opcode
java -jar baksmali.jar search app.apk --opcode "const-string,*,invoke-virtual"

# 类正则过滤（只搜匹配的类，作用于类型描述符）
java -jar baksmali.jar search app.apk --class "Lcom/example/.*" --opcode invoke-virtual

# 方法名正则过滤
java -jar baksmali.jar search app.apk --method "onCreate" --opcode invoke-virtual

# 默认就是 JSON（机器可读）；要人读文本显式 --format text
java -jar baksmali.jar search app.apk --opcode const-string,invoke-virtual --format text

# 不指定 --opcode：按 --class/--method 正则列举匹配的方法
java -jar baksmali.jar search app.apk --class "Lcom/.*" --method "onCreate"
```

## 📤 输出格式

**JSON（默认）**，面向 Agent / 脚本：

```json
[
  {"caller":"Lcom/Example;->greet()V","offset":"0x2","instructions":["const-string \"hello\"","invoke-virtual ..."]}
]
```

**文本模式**（`--format text`），每个匹配输出 `类->方法 @ offset` 后跟缩进指令：

```
Lcom/Example;->greet()V @ offset 0x2
  const-string "hello"
  invoke-virtual Ljava/lang/StringBuilder;->append(...)...
```

`offset` 是匹配序列第一条指令在方法体内的字节偏移（hex）。`Match` 数据结构见 `PatternSearcher.java:63`。

## 🔬 真实示例：合成访问器

用仓库自带的 `accessorTest.dex` fixture（含大量 `invoke-static` 合成访问器调用）：

```bash
java -jar baksmali.jar search \
  dexlib2/src/test/resources/accessorTest.dex \
  --opcode invoke-static
```

实际输出（默认 JSON，节选）：

```json
[
  {"caller":"Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_and(Z)V","offset":"0x2","instructions":["invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$072(Lorg/jf/dexlib2/AccessorTypes;I)Z"]},
  {"caller":"Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_or(Z)V","offset":"0x2","instructions":["invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$076(Lorg/jf/dexlib2/AccessorTypes;I)Z"]}
]
```

人读文本对照（`--format text`）：

```
Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_and(Z)V @ offset 0x2
  invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$072(Lorg/jf/dexlib2/AccessorTypes;I)Z
Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_or(Z)V @ offset 0x2
  invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$076(Lorg/jf/dexlib2/AccessorTypes;I)Z
```

每个 `Accessors` 内部类方法都在偏移 `0x2` 处通过 `invoke-static` 调用一个 `access$NNN` 桥接方法——这是 Java 内部类访问外部类私有成员的标准编译产物。

## 📐 匹配规则

| 规则 | 说明 |
|------|------|
| 连续匹配 | 模式中每个 token 必须与方法中连续的指令一一对应 |
| `*` 通配 | 匹配任意一条 opcode（占位）。如 `const-string,*,return-void` 中 `*` 匹配两者之间的那条指令 |
| 重叠匹配 | 从每个起始位置都尝试，会报告重叠的匹配（步进 1，见 `PatternSearcher.java:151`） |
| 大小写不敏感 | opcode 名（`invoke-virtual`、`INVOKE-VIRTUAL` 等价） |
| 类/方法正则 | 用 `find()`（部分匹配）；`--class` 作用于类型描述符，`--method` 作用于方法名 |

## 🎯 适用场景

| 场景 | 命令 |
|------|------|
| 找日志调用点 | `--opcode "const-string,invoke-virtual"` + grep 日志 tag |
| 找字符串拼接 | `--opcode "new-instance,invoke-direct,const-string,invoke-virtual"`（StringBuilder） |
| 找反射调用 | `--opcode "invoke-virtual"` + `--method "invoke"` + `--class "Ljava/lang/reflect/.*"` |
| 找某类的所有方法 | `--class "Lcom/example/.*"`（无 `--opcode`，列举模式） |
| 找入口方法 | `--method "main"` |
| 找合成访问器 | `--opcode invoke-static` + `--class ".*\\$.*"` 定位内部类 |

## 🔗 与相关 skill 的关系

| Skill | 关系 |
|-------|------|
| [`dex-xref`](./dex-xref) | `search` 按 **opcode 模式** 正向搜索（找「什么样的指令」）；`xref` 按 **引用目标** 反向查询（找「谁调用了这个方法」）。互补不重叠 |
| [`dex-read`](./dex-read) | `search` 复用 `dex-read` 的 `DexFileFactory → ClassDef → Method → Instruction` 访问链，叠加滑动窗口匹配 |
| [`dex-list-structure`](./dex-list-structure) | 不带 `--opcode` 时 `search` 退化为「按正则列举方法」，与 list 的枚举能力相邻 |

## 🛠️ 工作原理

`search` 命令把 `--opcode` 模式经 `parsePattern`（`PatternSearcher.java:91`）切分为 token 列表，对每个方法体把指令物化为 `List<Instruction>`（带 code offset），再以滑动窗口做子序列匹配：

- `matchesAt`（`:156`）从某个起始位置逐一比对 token 与指令 opcode，`*` token 直接放行。
- 命中后**步进 1** 而非跳过匹配长度，从而捕获重叠匹配。
- 命中指令经 `BaksmaliFormatter` 格式化为可读文本存入 `Match.instructions`。

## 📚 延伸阅读

- [CLI: search](../cli/search.md) — `baksmali search` 子命令完整用法
- [CLI: xref](../cli/xref.md) — 反向交叉引用 CLI，与本 skill 互补
- [Skill: dex-xref](./dex-xref.md) — 反向调用图构建
- [Skill: dex-read](./dex-read.md) — 读侧访问链基石
- Reference: PatternSearcher — 滑动窗口匹配源码剖析
- [SKILL.md 原文](https://github.com/android-security-engineer/smali-skills/blob/master/skills/dex-search)
