---
name: dex-xref
description: "Use when the user asks to: (1) find who calls a method (callers), (2) find who accesses a field, (3) find who references a type, (4) reverse cross-reference / xref queries on dex/apk, (5) build a reverse reference index. Triggers: xref, cross-reference, 交叉引用, 反向引用, callers, 谁调用, 谁访问, 谁引用, field-refs, type-refs, baksmali xref."
---

# dex-xref — 交叉引用查询（谁引用了什么）

反向引用查询：给定一个方法/字段/类型，找出 dex 中所有引用它的位置。这是 `list`（正向列举）
的补充——`list` 告诉你「有哪些方法」，`xref` 告诉你「谁调用了这个方法」。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 快速参考

```bash
# 谁调用了某方法（精确匹配）
java -jar baksmali.jar xref callers app.apk --target "Lcom/Example;->foo()V"

# 谁访问了某字段
java -jar baksmali.jar xref field-refs app.apk --target "Lcom/Example;->count:I"

# 谁引用了某类型（check-cast / new-instance / 类型引用）
java -jar baksmali.jar xref type-refs app.apk --target "Lcom/Example;"

# 子串匹配（不记得完整签名时）—— foo()V 会匹配任何含该子串的方法
java -jar baksmali.jar xref callers app.apk --target "foo()V"

# JSON 输出（适合 Agent 消费）
java -jar baksmali.jar xref callers app.apk --target "Lcom/Example;->foo()V" --format json

# 不指定 --target：列出该类型的所有目标及其引用点
java -jar baksmali.jar xref callers app.apk
```

## 子命令

| 子命令 | 别名 | 匹配的引用类型 |
|--------|------|----------------|
| `callers` | `caller`, `c` | 方法引用（invoke-*） |
| `field-refs` | `field-ref`, `f` | 字段引用（iget/iput/sget/sput） |
| `type-refs` | `type-ref`, `t` | 类型引用（check-cast/new-instance/instance-of 等） |

## 输出格式

文本模式：先输出目标，再缩进列出每个引用点（调用方法 + 字节偏移）：

```
Lcom/Example;->foo()V
  Lcom/App;->onCreate()V @ offset 0x4
  Lcom/App;->onResume()V @ offset 0x10
```

JSON 模式：

```json
[{"target":"Lcom/Example;->foo()V","sites":[{"caller":"Lcom/App;->onCreate()V","offset":"0x4"}]}]
```

`offset` 是引用指令在方法体内的字节偏移（hex），可用于定位到反汇编输出中的具体行。

## 匹配规则

- **精确匹配优先**：`--target` 值等于格式化后的引用描述符时直接命中。
- **子串回退**：无精确匹配时，对每个已知目标做 `contains` 子串匹配。故 `foo()V` 能匹配
  `Lcom/Example;->foo()V`、`Lcom/Other;->foo()V` 等。
- **类型过滤**：每个子命令只报告对应引用类型的目标（`callers` 不会报告字段命中）。

## 典型场景

| 场景 | 命令 |
|------|------|
| 找某 Activity 的所有启动点 | `xref callers --target "->startActivity(...)"` |
| 找某字段的写入点 | `xref field-refs --target "Lcom/Config;->token:Ljava/lang/String;"` |
| 找某类的所有实例化 | `xref type-refs --target "Lcom/Sensitive;"` |
| 找构造函数的所有调用者 | `xref callers --target "-><init>()V"` |
| 找某 SDK 方法的集成点 | `xref callers --target "Lcom/sdk/;->track("` |

## 工作原理

`xref` 遍历 dex 的所有 `ClassDef → Method → Instruction`，对每条 `ReferenceInstruction`
收集 `(被引用目标 → 引用位置列表)` 的反向映射。详见 dexlib2 的
`org.jf.baksmali.ReferenceFinder`。
