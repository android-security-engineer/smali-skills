---
name: smali-skills
description: "Use when the user asks to: (1) work with Android dex files (assemble/disassemble/deodex/dump/list/analyze/rewrite), (2) reverse engineer Android APKs, (3) modify Dalvik bytecode, (4) programmatically manipulate dex files. Triggers: smali, baksmali, dex, apk, 反汇编, 汇编, 去优化, 逆向, Android逆向, dalvik, dexlib2."
---

# smali-skills — Android dex 字节码工具集

Android Dalvik 字节码的汇编/反汇编/分析/变换工具集，基于 smali/baksmali/dexlib2。

## 3 层架构

```
Layer 3: Skills ← 你在这里
         高层能力封装，面向 AI Agent 的交互接口
         ↓ 通过 shell 调用
Layer 2: CLI 终端
         smali.jar / baksmali.jar 命令行工具
         ↓ 调用
Layer 1: dexlib2 库
         Java 库，核心 dex 读写能力
```

## 安装

### 一键安装（推荐）

```bash
# 从 GitHub Release 安装最新版
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/install.sh | bash

# 指定版本
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/install.sh | bash -s 2.5.2

# 自定义安装路径
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/install.sh | bash -s latest ~/.local/share/smali-skills ~/.claude/skills/smali-skills
```

安装后：
- CLI jar → `~/.local/share/smali-skills/smali.jar` 和 `baksmali.jar`
- Skills 文档 → `~/.claude/skills/smali-skills/`

### 手动下载

```bash
# 下载最新 release 的 jar
curl -fsSL -o smali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/smali.jar
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar

# 下载 skills 文档
mkdir -p ~/.claude/skills/smali-skills
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/skills.tar.gz | tar -xzf - -C ~/.claude/skills/smali-skills --strip-components=1
```

### 从源码构建

```bash
git clone https://github.com/android-security-engineer/smali-skills.git
cd smali-skills
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew build -x test -x javadoc
# jar 位置: smali/build/libs/smali.jar, baksmali/build/libs/baksmali.jar
```

## 环境变量

安装后设置环境变量以简化调用：

```bash
export SMALI_JAR="$HOME/.local/share/smali-skills/smali.jar"
export BAKSMALI_JAR="$HOME/.local/share/smali-skills/baksmali.jar"

# 然后可以用
java -jar $SMALI_JAR assemble -o out.dex src/
java -jar $BAKSMALI_JAR disassemble -o out app.apk
```

## Skill 索引（渐进式披露）

### 快速入门 — 最常用操作

| 你想做什么 | 用哪个 Skill | 快速命令 |
|-----------|-------------|---------|
| 反汇编 dex/apk 为 smali 文本 | `dex-disassemble` | `java -jar baksmali.jar d -o out app.apk` |
| 将 smali 汇编为 dex | `dex-assemble` | `java -jar smali.jar a -o out.dex src/` |
| 浏览 dex 中的字符串/方法/类 | `dex-list` | `java -jar baksmali.jar l strings app.apk` |

### 进阶操作 — 分析与变换

| 你想做什么 | 用哪个 Skill | 说明 |
|-----------|-------------|------|
| 去 odex 优化（使可重汇编） | `dex-deodex` | 将优化指令还原为标准指令 |
| 寄存器类型推断 | `dex-analyze` | 追踪寄存器在指令间的类型变化 |
| 重命名/重映射 dex 元素 | `dex-rewrite` | 用 dexlib2 rewriter 框架变换 dex |

### 专家级 — 原始格式与编程

| 你想做什么 | 用哪个 Skill | 说明 |
|-----------|-------------|------|
| 查看二进制结构 | `dex-dump` | 带注释的十六进制转储 |
| 编程操作 dex | `dex-references` | dexlib2 Java API 完整参考 |

## 典型工作流

### 反汇编 → 修改 → 重汇编

```bash
# 1. 反汇编
java -jar baksmali/build/libs/baksmali.jar d -o smali_out app.apk

# 2. 修改 smali 文件
vim smali_out/com/example/Main.smali

# 3. 重汇编
java -jar smali/build/libs/smali.jar a -o modified.dex smali_out/
```

### odex → 可编辑 smali

```bash
# 1. 去 odex
java -jar baksmali/build/libs/baksmali.jar deodex -o smali_out \
  --boot-class-path /system/framework/framework.jar app.odex

# 2. 修改并重汇编
java -jar smali/build/libs/smali.jar a -o modified.dex smali_out/
```

### 分析混淆代码

```bash
# 全量寄存器类型推断 + 顺序标签
java -jar baksmali/build/libs/baksmali.jar d -o out \
  -r ALL,FULLMERGE --sequential-labels obfuscated.apk
```

### 快速侦察 APK

```bash
# 列举字符串（搜索关键字）
java -jar baksmali/build/libs/baksmali.jar l strings app.apk | grep "key"

# 列举类
java -jar baksmali/build/libs/baksmali.jar l classes app.apk

# 列举方法
java -jar baksmali/build/libs/baksmali.jar l methods app.apk | grep "login"

# 多 dex 查看
java -jar baksmali/build/libs/baksmali.jar l dex multi_dex.apk
```

## CLI 命令速查

### smali（汇编器）

```bash
java -jar smali/build/libs/smali.jar assemble \
  -o <输出.dex> \        # 输出文件（默认 out.dex）
  -a <API级别> \         # API 级别（默认 15）
  -j <线程数> \          # 并行线程数
  --allow-odex-opcodes \ # 允许 odex 操作码
  --verbose \            # 详细错误
  <输入文件或目录>        # 递归搜索 .smali
```

### baksmali（反汇编器）

```bash
java -jar baksmali/build/libs/baksmali.jar <子命令> [选项] <输入文件>
```

| 子命令 | 别名 | 用途 |
|--------|------|------|
| `disassemble` | `d`, `dis` | 反汇编为 smali |
| `deodex` | `x`, `de` | 去 odex |
| `dump` | `du` | 十六进制转储 |
| `list` | `l` | 列举对象（见下） |

### baksmali list 子命令

| 子命令 | 别名 | 列举内容 |
|--------|------|---------|
| `strings` | `s` | 字符串表 |
| `methods` | `m` | 方法表 |
| `fields` | `f` | 字段表 |
| `types` | `t` | 类型表 |
| `classes` | `c` | 类列表 |
| `dex` | `d` | APK/OAT 中 dex 条目 |
| `vtables` | `v` | 虚方法表 |
| `fieldoffsets` | `fo` | 字段偏移 |
| `dependencies` | `deps` | odex/oat 依赖 |
