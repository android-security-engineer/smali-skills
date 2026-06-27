---
name: dex-list
description: "Use when the user asks to: (1) list strings/methods/fields/types/classes in a dex file, (2) enumerate dex entries in an APK or OAT, (3) show virtual method tables or field offsets, (4) list dependencies in an odex/oat file, (5) inspect dex metadata without full disassembly. Triggers: list, 列举, enumerate, 字符串表, 方法表, 字段表, 类列表, vtable."
---

# dex-list — 列举 dex 文件中的各种对象

快速浏览 dex 文件的元数据，无需完整反汇编。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 子命令总览

```bash
java -jar baksmali/build/libs/baksmali.jar list <子命令> <输入文件>
```

| 子命令 | 别名 | 用途 |
|--------|------|------|
| `strings` | `s`, `str`, `string` | 列举字符串表 |
| `methods` | `m`, `method` | 列举方法表 |
| `fields` | `f`, `field` | 列举字段表 |
| `types` | `t`, `type` | 列举类型 ID 表 |
| `classes` | `c`, `class` | 列举类 |
| `dex` | `d` | 列举 APK/OAT 中的 dex 条目 |
| `vtables` | `v`, `vtable` | 列举虚方法表 |
| `fieldoffsets` | `fo`, `fieldoffset` | 列举实例字段偏移 |
| `dependencies` | `deps`, `dep` | 列举 odex/oat 的存储依赖 |

## 详细用法

### 列举字符串

```bash
java -jar baksmali/build/libs/baksmali.jar list strings app.apk
java -jar baksmali/build/libs/baksmali.jar l s app.apk
```

输出 dex 字符串池中的所有字符串，包括类名、方法名、字段名、字符串常量等。

### 列举方法

```bash
java -jar baksmali/build/libs/baksmali.jar list methods app.apk
```

输出格式：`类名->方法名(参数类型)返回类型`

### 列举字段

```bash
java -jar baksmali/build/libs/baksmali.jar list fields app.apk
```

### 列举类型

```bash
java -jar baksmali/build/libs/baksmali.jar list types app.apk
```

输出所有类型描述符（如 `Lcom/example/Main;`、`I`、`[B`）。

### 列举类

```bash
java -jar baksmali/build/libs/baksmali.jar list classes app.apk
```

### 列举 APK/OAT 中的 dex 条目

```bash
java -jar baksmali/build/libs/baksmali.jar list dex app.apk
```

多 dex APK 会列出 `classes.dex`、`classes2.dex` 等。OAT 文件会列出内含的所有 dex 条目路径。

### 列举虚方法表

```bash
java -jar baksmali/build/libs/baksmali.jar list vtables app.apk
```

需要类路径支持（加 `--boot-class-path`）。显示每个类的虚方法分发表。

### 列举实例字段偏移

```bash
java -jar baksmali/build/libs/baksmali.jar list fieldoffsets app.apk
```

需要类路径支持。显示每个类的实例字段在对象中的偏移量，用于理解内存布局。

### 列举 odex/oat 依赖

```bash
java -jar baksmali/build/libs/baksmali.jar list dependencies app.odex
```

仅适用于 odex/oat 文件，显示编译时记录的依赖信息。

## 类路径选项

`vtables` 和 `fieldoffsets` 需要类路径来构建类型层次：

```bash
java -jar baksmali/build/libs/baksmali.jar list vtables \
  --boot-class-path /system/framework/framework.jar \
  app.apk
```

## 实用技巧

```bash
# 快速搜索字符串
java -jar baksmali/build/libs/baksmali.jar l s app.apk | grep "password"

# 查找特定类的方法
java -jar baksmali/build/libs/baksmali.jar l m app.apk | grep "com/example"

# 多 dex APK 查看包含哪些 dex
java -jar baksmali/build/libs/baksmali.jar l dex multi_dex.apk

# 导出字符串表用于后续分析
java -jar baksmali/build/libs/baksmali.jar l strings app.apk > strings.txt
```
