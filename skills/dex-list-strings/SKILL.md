---
name: dex-list-strings
description: "Use when the user asks to: (1) list or search strings in a dex/apk file, (2) find string constants in Android bytecode, (3) extract the string table from a dex file, (4) grep for specific strings in an APK. Triggers: list strings, 字符串表, string table, 字符串列举, search strings, find string, 字符串搜索, baksmali list s."
---

# dex-list-strings — 列举 dex 文件中的字符串

快速提取 dex/apk 文件中的所有字符串常量，无需完整反汇编。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 快速参考

```bash
# 列举所有字符串（默认 JSON，每项 {"string": "..."}）
java -jar baksmali.jar list strings app.apk

# 短别名
java -jar baksmali.jar l s app.apk

# 人读文本：每行一个字符串（带引号）
java -jar baksmali.jar l s app.apk --format text
```

## 输出格式

默认 **JSON**（机器可读，面向 Agent / 脚本）。人读文本加 `--format text`，每行一个字符串：

```
"Hello World!"
"Lcom/example/Main;"
"password123"
"https://api.example.com/v1/login"
```

字符串池包含：
- 字符串常量（代码中的 `"..."`）
- 类名描述符（`Lcom/example/Main;`）
- 方法名、字段名
- 方法签名（`(Ljava/lang/String;)V`）
- URL、路径等硬编码值

## 真实示例

用 `LocalTest/classes.dex` fixture（含方法签名、类描述符、一个带特殊字符的本地变量名）：

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

可见字符串池里既有类型描述符（`LLocalTest;`）、方法名（`method1`），也有调试用的本地变量名——JSON 中 `\n` 已转义，便于脚本直接消费。

## 实用技巧

### 搜索关键字

默认 JSON 适合用 `jq` 精确取值；要传统 `grep` 文本过滤，先切到 `--format text`：

```bash
# JSON + jq：取所有字符串值里含 password 的（大小写不敏感）
java -jar baksmali.jar l s app.apk | jq -r '.[].string | select(test("password";"i"))'

# 文本 + grep：搜索 URL
java -jar baksmali.jar l s app.apk --format text | grep "https\?://"

# 文本 + grep：搜索加密相关
java -jar baksmali.jar l s app.apk --format text | grep -iE "aes|rsa|cipher|encrypt|decrypt|key|secret|token"
```

### 导出分析

```bash
# 导出为纯文本（每行一个字符串）
java -jar baksmali.jar l s app.apk --format text > strings.txt

# 统计字符串数量
java -jar baksmali.jar l s app.apk --format text | wc -l
# 或用 JSON + jq：
java -jar baksmali.jar l s app.apk | jq 'length'

# 去重统计
java -jar baksmali.jar l s app.apk --format text | sort -u | wc -l

# 提取所有类名
java -jar baksmali.jar l s app.apk --format text | grep "^\"L.*;\"$"
```

### 多 dex APK

```bash
# 先查看 APK 包含哪些 dex
java -jar baksmali.jar l dex app.apk

# 列举特定 dex 的字符串
java -jar baksmali.jar l s "app.apk/classes2.dex"
```

## 典型场景

| 场景 | 命令 |
|------|------|
| 快速侦察 APK | `java -jar baksmali.jar l s app.apk --format text \| head -50` |
| 查找硬编码密钥 | `java -jar baksmali.jar l s app.apk --format text \| grep -i "key\|secret"` |
| 查找网络端点 | `java -jar baksmali.jar l s app.apk --format text \| grep "http"` |
| 查找日志标签 | `java -jar baksmali.jar l s app.apk --format text \| grep "^\"TAG\|^\"LOG"` |
| 提取类名列表 | `java -jar baksmali.jar l s app.apk --format text \| grep "^\"L.*;\"$" > classes.txt` |
