---
name: dex-list-structure
description: "Use when the user asks to: (1) list dex entries in a multi-dex APK or OAT file, (2) show virtual method tables (vtables), (3) show instance field offsets, (4) list odex/oat dependencies, (5) understand class hierarchy or object memory layout. Triggers: list dex, 多dex, list vtables, 虚方法表, list fieldoffsets, 字段偏移, list dependencies, odex依赖, vtable, memory layout, 内存布局, baksmali list d, baksmali list v, baksmali list fo, baksmali list deps."
---

# dex-list-structure — 列举 dex 结构信息（多 dex / vtable / 字段偏移 / 依赖）

浏览 dex/apk/oat 文件的结构级信息：多 dex 条目、虚方法表、字段偏移、odex 依赖。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 列举 dex 条目（多 dex APK / OAT）

```bash
# 列举 APK/OAT 中包含的 dex 文件
java -jar baksmali.jar list dex app.apk

# 短别名
java -jar baksmali.jar l d app.apk
```

输出（文件名列表，纯文本，无 `--format` 选项）：

```
classes.dex
classes2.dex
```

### 真实示例

把两个 fixture dex 打成一个多 dex APK 后列举：

```bash
# 造一个最小多 dex apk
cp dexlib2/src/test/resources/accessorTest.dex /tmp/classes.dex
cp baksmali/src/test/resources/LocalTest/classes.dex /tmp/classes2.dex
( cd /tmp && jar cf multidex.apk classes.dex classes2.dex )

# 列举其中的 dex 条目
java -jar baksmali.jar list dex /tmp/multidex.apk
```

实际输出：

```
classes.dex
classes2.dex
```

### 用途

- 确认 APK 是否为多 dex
- 获取特定 dex 条目名，用于其他命令的输入路径

```bash
# 确认后，指定特定 dex 进行操作
java -jar baksmali.jar d -o out "app.apk/classes2.dex"
java -jar baksmali.jar l s "app.apk/classes2.dex"
```

## 列举虚方法表（vtables）

```bash
# 基本用法
java -jar baksmali.jar list vtables app.apk

# 短别名
java -jar baksmali.jar l v app.apk

# 指定类路径（通常需要）
java -jar baksmali.jar l v \
  --boot-class-path /system/framework/framework.jar \
  app.apk

# 只看特定类的 vtable
java -jar baksmali.jar l v --classes Lcom/example/Main app.apk

# 指定 OAT 版本覆盖
java -jar baksmali.jar l v --override-oat-version 56 app.apk
```

### 输出格式

显示每个类的虚方法分发表，包括继承的方法：

```
Lcom/example/Main; -> vtable
  0: Ljava/lang/Object;-><init>()V
  1: Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
  2: Lcom/example/Main;->onCreate(Landroid/os/Bundle;)V  # override
  ...
```

### 类路径选项

vtables 需要类路径来构建类型层次：

| 选项 | 说明 |
|------|------|
| `-b,--bootclasspath` | 引导类路径（冒号分隔） |
| `-c,--classpath` | 额外类路径 |
| `-d,--classpath-dir` | 类路径搜索目录 |
| `--check-package-private-access` | 检查包私有访问（4.2.0 odex 需要） |
| `--override-oat-version` | 覆盖 OAT 版本 |

## 列举实例字段偏移

```bash
# 基本用法
java -jar baksmali.jar list fieldoffsets app.apk

# 短别名
java -jar baksmali.jar l fo app.apk

# 指定类路径
java -jar baksmali.jar l fo \
  --boot-class-path /system/framework/framework.jar \
  app.apk
```

### 输出格式

显示每个类的实例字段在对象内存中的偏移量：

```
Lcom/example/Main;:
  0: Lcom/example/Main;->mContext:Landroid/content/Context;
  4: Lcom/example/Main;->mTitle:Ljava/lang/String;
  8: Lcom/example/Main;->mCount:I
```

偏移量从 0 开始，每个引用类型占 4 字节（32 位），基本类型按大小对齐。

### 用途

- 理解对象内存布局
- 分析 ART 运行时的字段访问模式
- 调试 iget-quick/iput-quick 等 odex 指令

## 列举 odex/oat 依赖

```bash
# 仅适用于 odex/oat 文件
java -jar baksmali.jar list dependencies app.odex

# 短别名
java -jar baksmali.jar l deps app.oat
```

输出编译时记录的依赖信息，包括：
- 依赖的框架 jar 文件
- 编译时的类路径
- OAT 文件的依赖关系

### 用途

- 确定 deodex 需要哪些框架文件
- 分析 OAT 编译依赖
- 排查 deodex 失败的原因

## 典型场景

| 场景 | 命令 |
|------|------|
| 确认 APK 是否多 dex | `java -jar baksmali.jar l d app.apk` |
| 查看类继承结构 | `java -jar baksmali.jar l v -b framework.jar app.apk` |
| 分析对象内存布局 | `java -jar baksmali.jar l fo -b framework.jar app.apk` |
| 排查 deodex 依赖 | `java -jar baksmali.jar l deps app.odex` |
| 查看特定类的 vtable | `java -jar baksmali.jar l v --classes Lcom/example/Main app.apk` |
