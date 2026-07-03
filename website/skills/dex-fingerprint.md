---
title: dex-fingerprint — opcode 指纹与库/克隆识别
description: 基于方法体 opcode 序列计算重命名无关指纹，精确去重与 n-gram 模糊匹配双模识别混淆后的已知库、重打包与抄袭代码。
outline: [2, 3]
---

# 🔏 dex-fingerprint — opcode 指纹与库/克隆识别

`dex-fingerprint` 封装 `baksmali fingerprint` 子命令：对每个方法体的 **opcode 序列**（不含任何名字/引用）计算指纹，因此**对重命名不敏感**——混淆器改类名、方法名、字段名、换寄存器号，只要字节码逻辑不变指纹就相同。这正是识别「被改名的已知库」「被重打包/抄袭的代码」的原理。

## 🧭 能力与工作流

```mermaid
flowchart TD
    DEX[("dex/apk/odex/oat")] --> FAC["DexFileFactory<br/>自动检测格式"]
    FAC --> CL["遍历 ClassDef"]
    REG["--class 正则<br/>过滤类型描述符"] -.->|过滤| CL
    CL --> IM["getInstructions<br/>取 opcode.name 序列"]

    subgraph 精确["精确模式 runList（默认）"]
        IM -->|SHA-256 前 64 位| MH["方法哈希 shortHash<br/>16 位 hex"]
        MH -->|方法哈希排序后再哈希| CH["类哈希"]
        CH -->|类哈希排序后再哈希| DH["dex 哈希"]
    end

    subgraph 模糊["匹配模式 runMatch --match ref.dex"]
        IM --> NG["ngrams n-gram 集合<br/>默认窗口 3"]
        NG --> CP["classNgramProfile<br/>类内多方法合并"]
        CP -->|加权 Jaccard| SIM["相似度 Σmin/Σmax"]
        REF["参考 dex 类画像"] -->|逐一比对| SIM
        SIM -->|≥ --min-similarity| MATCH["最佳匹配类 + 相似度"]
    end

    DH --> OUT[("默认 JSON<br/>{class,fingerprint}")]
    CH --> OUT
    MH --> OUT
    MATCH --> OUTM[("默认 JSON<br/>{class,match,similarity}")]
    OUT -.->.|"--format text"| TXT["人读文本"]
    OUTM -.->.|"--format text"| TXT

    style 精确 fill:#e8f5e9
    style 模糊 fill:#fce4ec
    style FAC fill:#fff3e0
    style SIM fill:#e3f2fd
```

两模式互补：**哈希判完全相同，n-gram 判轻微修改**。命令分发见 `baksmali/src/main/java/org/jf/baksmali/FingerprintCommand.java:128`（`--match` 在则走 `runMatch`，否则走 `runList`）。

## 📦 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
alias baksmali='java -jar baksmali.jar'
```

## 🚀 快速开始

```bash
# 列举指纹（默认类级，每个类一个 16 位 hex）
baksmali fingerprint app.apk
# 细到方法级
baksmali fingerprint app.apk --level method
# 整个 dex 一个哈希
baksmali fingerprint app.apk --level dex
# 只看某包（正则作用于类型描述符）
baksmali fingerprint app.apk --class 'Lokhttp3/.*'
# 默认就是 JSON（机器可读）；要人读文本加 --format text
baksmali fingerprint app.apk --format text
```

## 🎯 匹配参考库（--match）

把输入每个类用 **opcode n-gram 的加权 Jaccard 相似度**与参考 dex 的类逐一比较，报告 ≥ 阈值的最佳匹配。指向已知库的 dex，即可在 app 里「认出」该库——即使被混淆。

```bash
# 在 app 里找 okhttp（指向已知版本的 okhttp classes.dex）
baksmali fingerprint app.apk --match okhttp.dex --min-similarity 0.9
# 用 4-gram，更严格、越不容忍改动
baksmali fingerprint app.apk --match lib.dex --ngram 4
# 默认 JSON：[{class, match, similarity}]
```

| 选项 | 默认 | 说明 |
|------|------|------|
| `--ngram N` | `3` | n-gram 窗口大小，越大越严格 |
| `--min-similarity F` | `0.85` | 报告阈值，`1.0` = 完全一致 |
| `--level` | `class` | 列举模式粒度：`method`/`class`/`dex` |
| `--class` | 全部 | 正则过滤类型描述符，列举与匹配均生效 |
| `--format` | `json` | `json`（默认）/`text` |

加权 Jaccard = `Σ min(计数) / Σ max(计数)`，两侧空袋定义为 `1.0`。**为什么用 n-gram 而非哈希**：精确哈希只能判「完全相同」，库被插桩、加一两条指令哈希就变了；n-gram Jaccard 对小改动**鲁棒**，故适合识别「被轻微修改过的库」。

## 🔬 真实示例：合成访问器

用仓库自带的 `accessorTest.dex` fixture（含 `AccessorTypes` 及内部类 `AccessorTypes$Accessors`）：

```bash
java -jar baksmali.jar fingerprint dexlib2/src/test/resources/accessorTest.dex
```

实际输出（默认 JSON）：

```json
[
  {"class":"Lorg/jf/dexlib2/AccessorTypes$Accessors;","fingerprint":"63594b5c1fa69ee4"},
  {"class":"Lorg/jf/dexlib2/AccessorTypes;","fingerprint":"206203f971d55342"}
]
```

人读文本对照（`--format text`）：

```
63594b5c1fa69ee4  Lorg/jf/dexlib2/AccessorTypes$Accessors;
206203f971d55342  Lorg/jf/dexlib2/AccessorTypes;
```

`63594b5c1fa69ee4` 即「类内所有方法 opcode 序列排序后再哈希」的结果。把两份 dex 的指纹集合取交集，即可在**不含任何名字**的前提下找出结构相同的类。

## 🧪 典型场景

**查抄袭/重打包**——同一份代码被改名后重新发布，类哈希取交集即「结构相同（可能仅改名）」的类：

```bash
baksmali fingerprint appA.apk | jq -r '.[].fingerprint' | sort > a.txt
baksmali fingerprint appB.apk | jq -r '.[].fingerprint' | sort > b.txt
comm -12 a.txt b.txt   # 共有指纹 = 疑似复用代码
```

**方法去重**——统计一个 dex 里有多少组「字节码完全相同」的方法：

```bash
baksmali fingerprint app.apk --level method \
  | jq -r '.[].fingerprint' | sort | uniq -d
```

| 场景 | 命令 | 用哪模式 |
|------|------|----------|
| 识别被混淆的第三方库 | `fingerprint app.apk --match sdk.dex --min-similarity 0.8` | 模糊 |
| 查重打包/抄袭 | 两 app 类指纹取 `comm` 交集 | 精确 |
| 方法体去重统计 | `--level method` + `sort \| uniq -d` | 精确 |
| 监控版本间结构变化 | `--level dex` 比较 dex 哈希 | 精确 |
| 锁定某包指纹基线 | `--class 'Lcom/foo/.*'` 导出类哈希 | 精确 |

## 🛠️ 工作原理

核心为纯模型 `org.jf.baksmali.fingerprint.Fingerprint`（无 I/O）：

- `opcodeSequence`（`Fingerprint.java:79`）：取 `MethodImplementation.getInstructions()` 的 `Opcode.name`；abstract/native 方法为空序列。
- `methodHash`（`:94`）：方法 opcode 序列哈希；`classHash`（`:103`）将**所有方法哈希排序后**再哈希（与方法声明顺序、类名、成员名都无关）；`dexHash`（`:116`）同理再上一级。
- `ngrams`（`:133`）：滑动窗口切 n-gram 计数袋；`classNgramProfile`（`:162`）合并类内多方法。
- `jaccard`（`:176`）：加权 Jaccard `Σmin/Σmax`，两侧空袋为 `1.0`。
- `shortHash`（`:220`）：SHA-256 前 64 位 → 16 位 hex 短哈希。

命令侧 `runList`（`FingerprintCommand.java:137`）按 `--level` 选 `methodHash`/`classHash`/`dexHash`；`runMatch`（`:214`）预建参考类画像（`:225`）后逐一算 Jaccard（`:242`）。

## 🔗 与相关 skill 的关系

| Skill | 关系 |
|-------|------|
| [`dex-diff`](./dex-diff) | `diff` 报两个 dex 间**方法体的精确 opcode 差异**（增删改指令）；`fingerprint` 只给整体哈希/相似度，不展开 diff。要细看差异用 `diff`，要批量查重用 `fingerprint` |
| [`dex-search`](./dex-search) | `search` 在方法体内按 **opcode 子序列**正向定位（找「什么样的指令」）；`fingerprint` 对整方法 opcode 序列算哈希/相似度。粒度正交 |
| [`dex-xref`](./dex-xref) | `xref` 按**引用目标**反向查询（谁调用了 X）；`fingerprint` 按**字节码结构**比对。一个看调用关系，一个看代码本身 |
| [`dex-read`](./dex-read) | `fingerprint` 复用 `dex-read` 的 `DexFileFactory → ClassDef → Method → Instruction` 访问链，叠加指纹与相似度计算 |

## 📚 延伸阅读

- [CLI: fingerprint](../cli/fingerprint.md) — `baksmali fingerprint` 子命令完整用法
- [CLI: diff](../cli/diff.md) — 精确 opcode diff CLI，与本 skill 互补
- [Reference: Fingerprint](../reference/baksmali/commands/fingerprint.md) — 纯模型源码剖析（`methodHash`/`jaccard` 等）
- [Skill: dex-diff](./dex-diff.md) — 两 dex 语义差异
- [Skill: dex-search](./dex-search.md) — 指令模式搜索
- [Skill: dex-read](./dex-read.md) — 读侧访问链基石
- [SKILL.md 原文](https://github.com/android-security-engineer/smali-skills/blob/master/skills/dex-fingerprint)
