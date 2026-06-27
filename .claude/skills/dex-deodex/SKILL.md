---
name: dex-deodex
description: "Use when the user asks to: (1) deodex an odex/oat file to standard smali, (2) convert optimized dex to reassemblable smali, (3) resolve optimized opcodes in odex/oat, (4) make odex editable and reassemblable. Triggers: deodex, 去优化, de-odex, convert odex, oat to smali, odex转smali, 还原odex."
---

# dex-deodex — 去 odex 优化，还原为可重汇编的 smali

将 odex/oat 文件中的优化指令还原为标准 Dalvik 指令，使输出可以重新汇编。

## 前置条件

```bash
# 方式1: 一键安装（推荐）
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/install.sh | bash

# 方式2: 仅下载 jar
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar

# 方式3: 从源码构建
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew :baksmali:build -x test -x javadoc
```

## 为什么需要 deodex

直接 `disassemble` odex/oat 文件会保留优化指令（如 `execute-inline`、`iget-quick` 等），这些指令：
- 无法被 smali 汇编器识别
- 丢失了原始的方法/字段引用信息
- 无法重新汇编回可用的 dex

`deodex` 命令通过类路径分析将这些优化指令解析为标准的 `invoke-virtual`、`iget` 等。

## 快速参考

```bash
# 去 odex（基本用法）
java -jar baksmali/build/libs/baksmali.jar deodex -o <输出目录> <odex/oat文件>

# 去 odex 并指定类路径
java -jar baksmali/build/libs/baksmali.jar deodex -o out \
  --boot-class-path /system/framework/framework.jar \
  app.odex

# 使用自定义内联方法表
java -jar baksmali/build/libs/baksmali.jar deodex -o out \
  --inline-table inline_methods.txt \
  app.odex
```

## 完整选项

```bash
java -jar baksmali/build/libs/baksmali.jar deodex \
  -o <输出目录> \                    # 输出目录（默认 out）
  -a <api级别> \                     # API 级别
  -j <线程数> \                      # 并行线程数
  --boot-class-path <jar列表> \     # 引导类路径（逗号分隔）
  --classpath <jar/dex列表> \       # 额外类路径
  --inline-table <文件> \           # 自定义内联方法表文件
  --check-package-private-access \  # 检查包私有访问
  --api-level <级别> \              # 指定 API 级别（影响类路径解析）
  <odex/oat文件>
```

> deodex 继承 disassemble 的所有选项（`--debug-info`、`--sequential-labels` 等）。

## 类路径解析

deodex 需要完整的类路径来解析优化指令。默认会搜索输入文件所在目录。

```bash
# 常见 Android 类路径
--boot-class-path /system/framework/framework.jar

# 多个类路径
--boot-class-path /system/framework/framework.jar,/system/framework/ext.jar

# 指定额外应用依赖
--classpath /path/to/libs/support.jar
```

## 自定义内联方法表

某些设备的 Dalvik 使用非标准的内联方法表。用 `deodexerant` 从设备导出：

```bash
# 在 Android 设备上运行 deodexerant
adb push deodexerant/deodexerant /data/local/
adb shell chmod +x /data/local/deodexerant
adb shell /data/local/deodexerant > inline_methods.txt

# 使用导出的内联表
java -jar baksmali/build/libs/baksmali.jar deodex -o out \
  --inline-table inline_methods.txt app.odex
```

## 典型工作流

```bash
# 1. 去 odex
java -jar baksmali/build/libs/baksmali.jar deodex -o smali_out \
  --boot-class-path /system/framework/framework.jar \
  app.odex

# 2. 验证输出可重汇编
java -jar smali/build/libs/smali.jar a -o rebuilt.dex smali_out/

# 3. 对比原始与重建
java -jar baksmali/build/libs/baksmali.jar d -o verified rebuilt.dex
```

## disassemble vs deodex

| | disassemble | deodex |
|---|---|---|
| 速度 | 快 | 较慢（需要类路径分析） |
| odex 指令 | 保留原始优化指令 | 解析为标准指令 |
| 可重汇编 | ❌ 不能 | ✅ 可以 |
| 需要类路径 | 仅 register-info 需要 | ✅ 必须 |
| 适用场景 | 快速查看 | 修改重打包 |

## 注意事项

- deodex 需要完整的类路径，缺失依赖会导致 `UnresolvedOdexInstruction` 错误。
- OAT 文件的 vdex 会自动在同目录查找（`<basename>.vdex`）。
- 不支持的 OAT 版本会抛出 `UnsupportedOatVersionException`。
