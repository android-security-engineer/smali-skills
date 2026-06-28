---
name: dex-disassemble
description: "Use when the user asks to: (1) disassemble a dex/apk/odex/oat file to smali text, (2) convert Android binary bytecode to readable smali format, (3) reverse engineer an APK's Dalvik bytecode, (4) decompile dex classes for analysis. Triggers: disassemble, 反汇编, disasm, baksmali, dex to smali, apk to smali, 反编译 dex."
---

# dex-disassemble — 反汇编 dex/apk 为 smali 文本

将 Android dex/apk/odex/oat 二进制文件反汇编为人类可读的 smali 文本。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 快速参考

```bash
# 基本反汇编
java -jar baksmali.jar disassemble -o <输出目录> <输入文件>

# 反汇编 APK（自动识别 zip 中的 classes.dex）
java -jar baksmali.jar d -o out app.apk

# 只反汇编特定类
java -jar baksmali.jar d -o out --classes Lcom/example/Main app.apk
```

## 支持的输入格式

| 格式 | 扩展名 | 说明 |
|------|--------|------|
| dex | `.dex` | 标准 Dalvik 可执行文件 |
| odex | `.odex` | 优化过的 dex（需 deodex 才能重汇编） |
| oat | `.oat` | ART 运行时格式（含 vdex 支持） |
| apk/zip | `.apk`, `.zip` | 自动提取 classes.dex |

## 完整选项

```bash
java -jar baksmali.jar disassemble \
  -o <输出目录> \                    # 输出目录（默认 out）
  -a <api级别> \                     # API 级别（默认 15）
  -j <线程数> \                      # 并行线程数
  --debug-info=false \              # 省略调试信息（.local/.param/.line）
  --code-offsets \                  # 每条指令前注释代码偏移
  --use-locals \                    # 用 .locals 代替 .registers
  --parameter-registers=false \     # 禁用 pNN 参数寄存器语法
  --sequential-labels \             # 标签用顺序编号而非字节码地址
  --implicit-references \           # 当前类方法/字段省略类名
  --accessor-comments=false \       # 禁用合成访问器辅助注释
  --normalize-virtual-methods \     # 虚方法引用归一化到声明基类
  --allow-odex-opcodes \            # 允许 odex 操作码（结果无法重汇编）
  --classes <类列表> \              # 只反汇编指定类（逗号分隔）
  --resolve-resources <前缀> <public.xml> \  # 解析资源 ID 引用
  -r <寄存器信息> \                 # 注释寄存器类型（ALL/ALLPRE/ALLPOST/ARGS/DEST/MERGE/FULLMERGE）
  <输入文件>
```

## 寄存器类型信息（-r 选项）

用于逆向分析时理解寄存器在每条指令前后的类型：

| 值 | 含义 |
|---|------|
| `ALL` | 指令前+后的完整寄存器类型 |
| `ALLPRE` | 仅指令前 |
| `ALLPOST` | 仅指令后 |
| `ARGS` | 方法参数寄存器 |
| `DEST` | 目标寄存器类型 |
| `MERGE` | 合并点寄存器类型（摘要） |
| `FULLMERGE` | 合并点完整类型集 |

## 资源 ID 解析

将字节码中的 `0x7f010001` 等资源 ID 解析为可读名称：

```bash
java -jar baksmali.jar d -o out \
  --resolve-resources android.R framework/res/values/public.xml \
  app.apk
```

可多次指定 `--resolve-resources` 以覆盖多个资源包。

## 典型工作流

```bash
# 1. 反汇编 APK
java -jar baksmali.jar d -o smali_out app.apk

# 2. 查看特定类的 smali 代码
cat smali_out/com/example/Main.smali

# 3. 带寄存器类型信息反汇编（深度分析）
java -jar baksmali.jar d -o out -r ALL app.apk

# 4. 省略调试信息（更干净的输出）
java -jar baksmali.jar d -o out --debug-info=false app.apk
```

## 注意事项

- 反汇编 odex/oat 文件时若未 deodex，输出将包含优化指令，**无法重新汇编**。此时应使用 `dex-deodex` skill。
- 多 dex APK 默认只处理 `classes.dex`。需要处理其他 dex 时，使用 `dex-list-structure` skill 查看条目后指定，或参考 `dex-multidex` skill。
- `--resolve-resources` 需要对应 Android 框架的 `public.xml` 文件。
