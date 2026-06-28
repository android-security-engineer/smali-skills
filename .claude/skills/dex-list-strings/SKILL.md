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
# 列举所有字符串
java -jar baksmali.jar list strings app.apk

# 短别名
java -jar baksmali.jar l s app.apk
```

## 输出格式

每行一个字符串，直接输出 dex 字符串池中的原始内容：

```
Hello World!
Lcom/example/Main;
println
(Ljava/lang/String;)V
password123
https://api.example.com/v1/login
```

字符串池包含：
- 字符串常量（代码中的 `"..."`）
- 类名描述符（`Lcom/example/Main;`）
- 方法名、字段名
- 方法签名（`(Ljava/lang/String;)V`）
- URL、路径等硬编码值

## 实用技巧

### 搜索关键字

```bash
# 搜索包含 "password" 的字符串
java -jar baksmali.jar l s app.apk | grep -i "password"

# 搜索 URL
java -jar baksmali.jar l s app.apk | grep "https\?://"

# 搜索 API 端点
java -jar baksmali.jar l s app.apk | grep -i "api\|endpoint\|url"

# 搜索加密相关
java -jar baksmali.jar l s app.apk | grep -iE "aes|rsa|cipher|encrypt|decrypt|key|secret|token"
```

### 导出分析

```bash
# 导出到文件
java -jar baksmali.jar l s app.apk > strings.txt

# 统计字符串数量
java -jar baksmali.jar l s app.apk | wc -l

# 去重统计
java -jar baksmali.jar l s app.apk | sort -u | wc -l

# 提取所有类名
java -jar baksmali.jar l s app.apk | grep "^L.*;$"
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
| 快速侦察 APK | `java -jar baksmali.jar l s app.apk \| head -50` |
| 查找硬编码密钥 | `java -jar baksmali.jar l s app.apk \| grep -i "key\|secret"` |
| 查找网络端点 | `java -jar baksmali.jar l s app.apk \| grep "http"` |
| 查找日志标签 | `java -jar baksmali.jar l s app.apk \| grep "^TAG\|^LOG"` |
| 提取类名列表 | `java -jar baksmali.jar l s app.apk \| grep "^L.*;$" > classes.txt` |
