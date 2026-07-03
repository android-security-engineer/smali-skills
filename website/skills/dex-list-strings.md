---
title: dex-list-strings — 列举 dex 字符串池
description: 提取 dex/apk 字符串表中的全部常量、类名、方法名与签名，默认 JSON 面向 Agent，无需完整反汇编。
outline: [2, 3]
---

# 🔤 dex-list-strings — 列举字符串池

`dex-list-strings` 是 smali-skills 的**字符串表读取**工具：从 dex / apk / odex / oat 中一次性吐出整个字符串池（string table），不反汇编任何方法体。默认输出 JSON（每项 `{"string": ...}`），加 `--format text` 切回每行一个带引号字符串的传统人读格式。是 APK 侦察的第一步——硬编码密钥、URL 端点、日志 tag、类名描述符都从这里捞。

## 🧭 能力与工作流

```mermaid
flowchart LR
    DEX[("dex / apk / odex / oat")] --> FAC["DexFileFactory<br/>自动检测格式"]
    FAC --> REF["dexFile.getReferences<br/>ReferenceType.STRING"]
    REF --> POOL["字符串池遍历<br/>按 dex 出现顺序"]
    POOL --> MAT["物化为 List&lt;Reference&gt;<br/>便于计数/分组"]
    MAT --> JS["outputFormat.isJson()<br/>默认 true"]
    JS -->|"是"| JSON[("JSON 数组<br/>{\"string\": ...}<br/>面向 Agent/脚本")]
    JS -->|"否 (text)"| TXT["BaksmaliFormatter<br/>每行一个带引号字符串")]
    JSON -.->|jq 过滤| PIPE["grep / sort / wc"]
    TXT -.->|grep 过滤| PIPE

    style FAC fill:#fff3e0
    style REF fill:#e3f2fd
    style JSON fill:#e8f5e9
    style TXT fill:#e8f5e9
```

命令声明见 `baksmali/src/main/java/org/jf/baksmali/ListStringsCommand.java:46`（`extends ListReferencesCommand`，传 `ReferenceType.STRING`），输出分支与物化逻辑在父类 `baksmali/src/main/java/org/jf/baksmali/ListReferencesCommand.java:85`（遍历 `getReferences`）、`:106`（JSON 分支）与 `:116`（text 分支，走 `BaksmaliFormatter.getReference`）。

## 📦 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 🚀 快速开始

```bash
# 列举所有字符串（默认 JSON，每项 {"string": "..."}）
java -jar baksmali.jar list strings app.apk

# 短别名：l（list）s（strings）
java -jar baksmali.jar l s app.apk

# 人读文本：每行一个带引号字符串
java -jar baksmali.jar l s app.apk --format text
```

字符串池里**不只有代码里的 `"..."` 常量**，还包含：

| 类别 | 示例 |
|------|------|
| 字符串常量 | `"Hello World!"`、`"password123"` |
| 类名描述符 | `Lcom/example/Main;`、`LLocalTest;` |
| 方法名 / 字段名 | `method1`、`boolean_val` |
| 方法签名 | `(Ljava/lang/String;)V`、`(I,J,Ljava/lang/String;)V` |
| 类型短码 | `I`、`J`、`V`、`Z`（基本类型/void） |
| 硬编码值 | URL、文件路径、日志 tag |

## 🔬 真实示例

用仓库自带 `LocalTest/classes.dex` fixture（含方法签名、类描述符、一个带特殊字符的本地变量名）：

```bash
java -jar baksmali.jar list strings baksmali/src/test/resources/LocalTest/classes.dex
```

实际输出（默认 JSON，节选）：

```json
[
  {"string":"I"},
  {"string":"J"},
  {"string":"LLocalTest;"},
  {"string":"Ljava/lang/Object;"},
  {"string":"Ljava/lang/String;"},
  {"string":"V"},
  {"string":"VIJL"},
  {"string":"blah! This local name has some spaces, a colon, even a \nnewline!"},
  {"string":"method1"}
]
```

人读文本对照（`--format text`）：

```
"I"
"J"
"LLocalTest;"
"Ljava/lang/Object;"
"Ljava/lang/String;"
"V"
"VIJL"
"blah! This local name has some spaces, a colon, even a \nnewline!"
"method1"
```

注意 JSON 中 `\n` 已转义，脚本可直接 `jq` 消费；`LLocalTest;` / `method1` / `VIJL` 等是编译器写入字符串池的类型描述符、方法名与方法签名（参数短码串），调试用的本地变量名（带空格/冒号/换行）也在其中——这正是字符串池作为「dex 全局 intern 表」的特性。

## 🛠️ 搜索与导出

### 关键字搜索

默认 JSON 用 `jq` 精确取值，要传统 `grep` 文本过滤先切 `--format text`：

```bash
# JSON + jq：取含 password 的字符串值（大小写不敏感）
java -jar baksmali.jar l s app.apk | jq -r '.[].string | select(test("password";"i"))'

# 文本 + grep：搜索 URL
java -jar baksmali.jar l s app.apk --format text | grep "https\?://"

# 文本 + grep：搜索加密相关
java -jar baksmali.jar l s app.apk --format text | grep -iE "aes|rsa|cipher|encrypt|decrypt|key|secret|token"
```

### 导出与统计

```bash
# 导出纯文本（每行一个字符串）
java -jar baksmali.jar l s app.apk --format text > strings.txt

# 统计数量：文本走 wc -l，JSON 走 jq length
java -jar baksmali.jar l s app.apk --format text | wc -l
java -jar baksmali.jar l s app.apk | jq 'length'

# 去重统计
java -jar baksmali.jar l s app.apk --format text | sort -u | wc -l

# 提取所有类名（形如 L...;）
java -jar baksmali.jar l s app.apk --format text | grep "^\"L.*;\"$" > classes.txt
```

## 🎯 适用场景

| 场景 | 命令 |
|------|------|
| 快速侦察 APK | `java -jar baksmali.jar l s app.apk --format text \| head -50` |
| 查找硬编码密钥 | `java -jar baksmali.jar l s app.apk --format text \| grep -i "key\|secret"` |
| 查找网络端点 | `java -jar baksmali.jar l s app.apk --format text \| grep "http"` |
| 查找日志 TAG | `java -jar baksmali.jar l s app.apk --format text \| grep "^\"TAG\|^\"LOG"` |
| 提取类名列表 | `java -jar baksmali.jar l s app.apk --format text \| grep "^\"L.*;\"$" > classes.txt` |

### 多 dex APK

字符串池是 **per-dex** 的，多 dex APK 需逐个列举：

```bash
# 先看 APK 含哪些 dex
java -jar baksmali.jar l dex app.apk

# 列举特定 dex 的字符串（路径后缀语法）
java -jar baksmali.jar l s "app.apk/classes2.dex"
```

## 🔗 与相关 skill 的关系

| Skill | 关系 |
|-------|------|
| [`dex-list-classes`](./dex-list-classes) | 同族。classes 列举**类定义**结构，strings 列举字符串池；类名描述符（`L...;`）两者都出现，可交叉印证 |
| [`dex-list-structure`](./dex-list-structure) | structure 是**结构级**纯文本输出（vtables/fieldoffsets/dependencies）；本 skill 是内容级，默认 JSON |
| [`dex-multidex`](./dex-multidex) | 字符串池 per-dex，多 dex 时用 `"app.apk/classes2.dex"` 后缀语法定位 |
| [`dex-dump`](./dex-dump) | dump 输出 `string_data_item` 原始字节与注释，可与本 skill 的结构化字符串列表交叉印证 |
| [`dex-read`](./dex-read) | 本 skill 复用 `dex-read` 的 `DexFileFactory` 解析基石，但只读字符串引用不展开方法体 |
| [`dex-search`](./dex-search) | search 定位「字符串被谁加载」（`const-string,invoke-virtual` 指令模式）；本 skill 只列出字符串本身，不关联调用点 |

## 📚 延伸阅读

- [CLI: baksmali list](../cli/list.md) — `list` 全部子命令（classes/methods/strings/fields/types/dex...）总览
- [Reference: ListStringsCommand](../reference/baksmali/commands/list-strings.md) — 字符串列举命令源码剖析
- [Reference: ListReferencesCommand](../reference/baksmali/commands/list.md) — 父类输出/聚合/格式分支逻辑
- [Skill: dex-list-classes](./dex-list-classes.md) — 类/类型/字段列举（同族）
- [Skill: dex-multidex](./dex-multidex.md) — 多 dex 定位语法
- [Skill: dex-dump](./dex-dump.md) — 原始 dex 字节级 dump
- [SKILL.md 原文](https://github.com/android-security-engineer/smali-skills/blob/master/skills/dex-list-strings)
