---
name: dex-list-methods
description: "Use when the user asks to: (1) list methods in a dex/apk file, (2) find or search method names in Android bytecode, (3) enumerate the method table, (4) look up method signatures. Triggers: list methods, 方法表, method table, 方法列举, find method, search method, 方法搜索, baksmali list m."
---

# dex-list-methods — 列举 dex 文件中的方法

快速提取 dex/apk 文件中的所有方法引用，无需完整反汇编。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 快速参考

```bash
# 列举所有方法（默认 JSON，每项含 class/name/parameters/returnType）
java -jar baksmali.jar list methods app.apk

# 短别名
java -jar baksmali.jar l m app.apk

# 人读文本：每行一个方法引用 类名->方法名(参数)返回类型
java -jar baksmali.jar list methods --format text app.apk
```

## 输出格式

默认 **JSON**（机器可读，面向 Agent / 脚本）。人读文本加 `--format text`，每行一个方法引用：

```
Lcom/example/Main;->onCreate(Landroid/os/Bundle;)V
Lcom/example/Main;->login(Ljava/lang/String;Ljava/lang/String;)Z
Ljava/lang/Object;-><init>()V
```

JSON schema（默认）：

```json
[{"class":"Lcom/Example;","name":"foo","parameters":["I"],"returnType":"V"}]
```

## 聚合选项（原生支持，无需 grep/wc 管道）

```bash
# 仅输出方法总数（默认 JSON: {"count":N}；text: 单数字）
java -jar baksmali.jar list methods --count app.apk

# 按定义类分组计数（默认 JSON: [{group,count}]；text: "数量\t类"）
java -jar baksmali.jar list methods --group-by class app.apk
```

## 真实示例

用 `LocalTest/classes.dex` fixture（仅一个类 `LLocalTest;`，含 `method1` 与 `method2`）：

```bash
java -jar baksmali.jar list methods baksmali/src/test/resources/LocalTest/classes.dex
```

实际输出（默认 JSON）：

```json
[
  {"class":"LLocalTest;","name":"method1","parameters":[],"returnType":"V"},
  {"class":"LLocalTest;","name":"method2","parameters":["I","J","Ljava/lang/String;"],"returnType":"V"}
]
```

人读文本对照（`--format text`）：

```
LLocalTest;->method1()V
LLocalTest;->method2(IJLjava/lang/String;)V
```

聚合示例（用方法更多的 `accessorTest.dex`，432 个方法）：

```bash
java -jar baksmali.jar list methods --count dexlib2/src/test/resources/accessorTest.dex
# => {"count":432}

java -jar baksmali.jar list methods --group-by class dexlib2/src/test/resources/accessorTest.dex
# => [{"group":"Ljava/lang/Object;","count":1},{"group":"Lorg/jf/dexlib2/AccessorTypes$Accessors;","count":232},{"group":"Lorg/jf/dexlib2/AccessorTypes;","count":199}]
```

> 交叉引用（谁调用了某方法）见 [[dex-xref]]；指令序列搜索见 [[dex-search]]。

## 实用技巧

### 搜索特定方法

```bash
# 搜索特定类的方法
java -jar baksmali.jar l m app.apk | grep "com/example"

# 搜索构造函数
java -jar baksmali.jar l m app.apk | grep "-><init>"

# 搜索特定方法名
java -jar baksmali.jar l m app.apk | grep "->login\b"

# 搜索 onClick 处理器
java -jar baksmali.jar l m app.apk | grep "->onClick"
```

### 按签名模式搜索

```bash
# 搜索返回 String 的方法
java -jar baksmali.jar l m app.apk | grep ")Ljava/lang/String;$"

# 搜索接受 Context 参数的方法
java -jar baksmali.jar l m app.apk | grep "Landroid/content/Context;"

# 搜索静态方法（需结合反汇编确认）
java -jar baksmali.jar l m app.apk | grep "Lcom/example.*->.*\)"
```

### 导出分析

```bash
# 导出到文件
java -jar baksmali.jar l m app.apk > methods.txt

# 统计方法数量
java -jar baksmali.jar l m app.apk | wc -l

# 按类分组统计
java -jar baksmali.jar l m app.apk | cut -d'-' -f1 | sort | uniq -c | sort -rn | head -20

# 提取类名列表
java -jar baksmali.jar l m app.apk | cut -d'-' -f1 | sort -u
```

### 多 dex APK

```bash
# 列举特定 dex 的方法
java -jar baksmali.jar l m "app.apk/classes2.dex"
```

## 典型场景

| 场景 | 命令 |
|------|------|
| 查找入口方法 | `java -jar baksmali.jar l m app.apk \| grep "->main("` |
| 查找生命周期方法 | `java -jar baksmali.jar l m app.apk \| grep "->onCreate\|->onResume\|->onPause"` |
| 查找网络方法 | `java -jar baksmali.jar l m app.apk \| grep -i "http\|request\|fetch\|upload"` |
| 查找加密方法 | `java -jar baksmali.jar l m app.apk \| grep -iE "cipher\|encrypt\|decrypt\|digest"` |
| 分析类的方法数量 | `java -jar baksmali.jar l m app.apk \| cut -d'-' -f1 \| sort \| uniq -c \| sort -rn` |
