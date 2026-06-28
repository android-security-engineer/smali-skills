---
name: dex-classpath
description: "Use when the user asks to: (1) configure classpath for deodex or register-info analysis, (2) resolve framework dependencies, (3) pull and use Android framework files, (4) fix UnresolvedOdexInstruction errors, (5) understand classpath resolution for ART vs Dalvik. Triggers: classpath, 类路径, bootclasspath, boot-class-path, framework.jar, boot.oat, odex依赖, deodex classpath, ART classpath, Dalvik classpath, --classpath-dir, UnresolvedOdexInstruction."
---

# dex-classpath — 类路径配置与解析

deodex 和寄存器类型分析需要完整的类路径来构建类型层次。本文档说明如何正确配置。

## 何时需要类路径

| 操作 | 需要类路径 | 说明 |
|------|-----------|------|
| `deodex` | ✅ 必须 | 解析优化指令需要完整类型层次 |
| `disassemble -r` | ✅ 必须 | 寄存器类型推断需要类型信息 |
| `list vtables` | ✅ 必须 | 虚方法表需要类型层次 |
| `list fieldoffsets` | ✅ 必须 | 字段偏移需要类型层次 |
| `disassemble` (无 -r) | ❌ 不需要 | 纯反汇编不需要 |
| `list strings/methods/fields/types/classes` | ❌ 不需要 | 仅列举元数据 |
| `dump` | ❌ 不需要 | 十六进制转储不需要 |

## 类路径选项

所有需要类路径的命令共享以下选项：

| 选项 | 短选项 | 说明 |
|------|--------|------|
| `--bootclasspath` | `-b` | 引导类路径（冒号分隔） |
| `--classpath` | `-c` | 额外类路径（冒号分隔） |
| `--classpath-dir` | `-d` | 类路径搜索目录 |
| `--api` | `-a` | API 级别（影响默认类路径） |

## ART 设备（Android L+）

```bash
# 方式1：指定 boot.oat（推荐）
adb pull /system/framework/arm/boot.oat /tmp/boot.oat
java -jar baksmali.jar deodex -b /tmp/boot.oat -o out app.odex

# 方式2：拉取整个框架目录
adb pull /system/framework/arm /tmp/framework
java -jar baksmali.jar deodex -b /tmp/framework/boot.oat -o out app.odex
```

### Android N+ 的变化

Android N 将 `boot.oat` 拆分为多个文件。只需指定 `boot.oat`，同目录的其他文件会自动加载：

```bash
adb pull /system/framework/arm /tmp/framework
java -jar baksmali.jar deodex -b /tmp/framework/boot.oat -o out app.odex
```

## Dalvik 设备（Android L 之前）

```bash
# 方式1：指定框架目录（推荐）
adb pull /system/framework /tmp/framework
java -jar baksmali.jar deodex -d /tmp/framework -o out app.odex

# 方式2：使用 BOOTCLASSPATH 环境变量
export BOOTCLASSPATH=$(adb shell "echo \$BOOTCLASSPATH")
java -jar baksmali.jar deodex -b "$BOOTCLASSPATH" -d /tmp/framework -o out app.odex
```

## 类路径条目格式

`--bootclasspath` / `--classpath` 接受冒号分隔的列表，每个条目可以是：

| 格式 | 示例 | 说明 |
|------|------|------|
| 简单文件名 | `framework.jar` | 需配合 `--classpath-dir` |
| 设备路径 | `/system/framework/framework.jar` | 需配合 `--classpath-dir` |
| 本地路径 | `/tmp/framework/framework.jar` | 不需要 `--classpath-dir` |

## 常见问题

### UnresolvedOdexInstruction

```
Error: UnresolvedOdexInstruction
```

**原因**：类路径不完整，缺少依赖的框架类。

**解决**：
1. 确保提供了完整的 `--bootclasspath`
2. 使用 `list dependencies` 查看需要的依赖
3. 拉取所有依赖到本地

```bash
# 查看需要的依赖
java -jar baksmali.jar l deps app.odex

# 提供完整类路径
java -jar baksmali.jar deodex \
  -b /tmp/framework/boot.oat \
  -c /tmp/app/libs/support.jar \
  -o out app.odex
```

### 类路径默认值

不指定 `--bootclasspath` 时，baksmali 会尝试根据 `-a` API 级别选择合理的默认框架文件列表。如果默认不正确，需要手动指定。

### 空类路径

某些场景需要空类路径（不加载任何框架类）：

```bash
java -jar baksmali.jar deodex -b "" -o out app.odex
```

## 完整示例

### 从设备拉取框架并 deodex

```bash
# 1. 从设备拉取框架文件
adb pull /system/framework /tmp/framework

# 2. deodex（ART 设备）
adb pull /system/framework/arm/boot.oat /tmp/boot.oat
java -jar baksmali.jar deodex \
  -b /tmp/boot.oat \
  -d /tmp/framework \
  -o smali_out \
  app.odex

# 3. 验证输出
java -jar smali.jar a -o rebuilt.dex smali_out/
```

### 带类路径的寄存器类型分析

```bash
# 拉取框架
adb pull /system/framework /tmp/framework

# 反汇编 + 寄存器类型信息
java -jar baksmali.jar d \
  -o out \
  -r ALL \
  -b /tmp/framework/boot.oat \
  app.apk
```
