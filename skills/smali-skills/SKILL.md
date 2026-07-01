---
name: smali-skills
description: "Use when the user asks to: (1) work with Android dex files (assemble/disassemble/deodex/dump/list/analyze/rewrite), (2) reverse engineer Android APKs, (3) modify Dalvik bytecode, (4) programmatically manipulate dex files, (5) write or edit smali code. Triggers: smali, baksmali, dex, apk, 反汇编, 汇编, 去优化, 逆向, Android逆向, dalvik, dexlib2, smali语法, round-trip."
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

### Claude Code 插件（marketplace，推荐）

本仓库即一个 Claude Code marketplace，技能从 `skills/*/SKILL.md` 自动发现：

```
/plugin marketplace add android-security-engineer/smali-skills
/plugin install smali-skills@smali-skills
```

安装后以 `/smali-skills:<skill>` 调用（如 `/smali-skills:dex-xref`）。CLI jar 仍需另装（见下）。

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
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar

# 下载 skills 文档
mkdir -p ~/.claude/skills/smali-skills
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/skills.tar.gz | tar -xzf - -C ~/.claude/skills/smali-skills --strip-components=1
```

### 从源码构建

```bash
git clone https://github.com/android-security-engineer/smali-skills.git
cd smali-skills
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew build -x test -x javadoc
# jar 位置: smali.jar, baksmali.jar
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

### 🟢 快速入门 — 最常用操作

| 你想做什么 | 用哪个 Skill | 快速命令 |
|-----------|-------------|---------|
| 反汇编 dex/apk 为 smali 文本 | `dex-disassemble` | `java -jar baksmali.jar d -o out app.apk` |
| 将 smali 汇编为 dex | `dex-assemble` | `java -jar smali.jar a -o out.dex src/` |
| 浏览 dex 中的字符串 | `dex-list-strings` | `java -jar baksmali.jar l s app.apk` |
| 浏览 dex 中的方法 | `dex-list-methods` | `java -jar baksmali.jar l m app.apk` |
| 浏览 dex 中的类/类型/字段 | `dex-list-classes` | `java -jar baksmali.jar l c app.apk` |
| 结构化输出（JSON 默认 / 计数 / 分组） | `dex-list-methods` | `java -jar baksmali.jar l m app.apk`（默认 JSON）；`--count`；`--group-by class` |
| 谁调用了某方法 | `dex-xref` | `java -jar baksmali.jar xref callers --target "Lc;->foo()V" app.apk` |
| 搜索指令序列 | `dex-search` | `java -jar baksmali.jar search --opcode const-string,invoke-virtual app.apk` |
| 在编辑器中获得 smali 诊断/大纲/悬浮 | `smali-lsp` | `java -jar smali.jar lsp`（LSP over stdio） |
| 把 dex 查询暴露给 AI Agent（MCP） | `smali-mcp` | `java -jar baksmali.jar mcp`（MCP over stdio） |
| 格式化 / 风格检查 smali 源码 | `smali-format` | `java -jar smali.jar format --write out/` · `... lint out/` |
| 反汇编→修改→重汇编 | `dex-roundtrip` | 见完整工作流 |

### 🟡 进阶操作 — 分析与变换

| 你想做什么 | 用哪个 Skill | 说明 |
|-----------|-------------|------|
| 去 odex 优化（使可重汇编） | `dex-deodex` | 将优化指令还原为标准指令 |
| 寄存器类型推断 | `dex-analyze` | 追踪寄存器在指令间的类型变化 |
| 重命名/重映射类型和引用 | `dex-rewrite-references` | 类名混淆/反混淆、API 重定向 |
| 修改方法体/注解/调试信息 | `dex-rewrite-structure` | 结构性元素变换 |
| 多 dex 文件处理 | `dex-multidex` | 指定 APK 中的特定 dex 条目 |
| 类路径配置 | `dex-classpath` | deodex/分析所需的框架依赖 |

### 🔴 专家级 — 原始格式与编程

| 你想做什么 | 用哪个 Skill | 说明 |
|-----------|-------------|------|
| 查看二进制结构 | `dex-dump` | 带注释的十六进制转储 |
| dex 结构信息（vtable/偏移/依赖） | `dex-list-structure` | 虚方法表、字段偏移、odex 依赖 |
| smali 语法参考 | `smali-syntax` | 指令、指令、类型描述符 |
| smali 语言服务器（编辑器集成） | `smali-lsp` | 诊断、文档符号大纲、opcode 悬浮文档 |
| 用 dexlib2 读取 dex | `dex-read` | 加载/遍历 dex 数据模型 |
| 用 dexlib2 构建 dex | `dex-build` | 从零构建 dex 文件 |
| 指令类型与 Opcode 版本 | `dex-instructions` | 指令接口、格式、版本映射 |

## 典型工作流

### 反汇编 → 修改 → 重汇编

```bash
# 1. 反汇编
java -jar baksmali.jar d -o smali_out app.apk

# 2. 修改 smali 文件
vim smali_out/com/example/Main.smali

# 3. 重汇编
java -jar smali.jar a -o modified.dex smali_out/
```

详见 `dex-roundtrip` skill。

### odex → 可编辑 smali

```bash
# 1. 去 odex
java -jar baksmali.jar deodex -o smali_out \
  -b /system/framework/boot.oat app.odex

# 2. 修改并重汇编
java -jar smali.jar a -o modified.dex smali_out/
```

详见 `dex-deodex` 和 `dex-classpath` skill。

### 分析混淆代码

```bash
# 全量寄存器类型推断 + 顺序标签
java -jar baksmali.jar d -o out \
  -r ALL,FULLMERGE --sequential-labels obfuscated.apk
```

详见 `dex-analyze` skill。

### 快速侦察 APK

```bash
# 列举字符串（搜索关键字）
java -jar baksmali.jar l s app.apk | grep "key"

# 列举类
java -jar baksmali.jar l c app.apk

# 列举方法
java -jar baksmali.jar l m app.apk | grep "login"

# 多 dex 查看
java -jar baksmali.jar l d multi_dex.apk
```

详见 `dex-list-strings`、`dex-list-methods`、`dex-list-classes` skill。

## CLI 命令速查

### smali（汇编器）

```bash
java -jar smali.jar assemble \
  -o <输出.dex> \        # 输出文件（默认 out.dex）
  -a <API级别> \         # API 级别（默认 15）
  -j <线程数> \          # 并行线程数
  --allow-odex-opcodes \ # 允许 odex 操作码
  --verbose \            # 详细错误
  <输入文件或目录>        # 递归搜索 .smali
```

### baksmali（反汇编器）

```bash
java -jar baksmali.jar <子命令> [选项] <输入文件>
```

| 子命令 | 别名 | 用途 |
|--------|------|------|
| `disassemble` | `d`, `dis` | 反汇编为 smali |
| `deodex` | `x`, `de` | 去 odex |
| `dump` | `du` | 十六进制转储 |
| `list` | `l` | 列举对象（见下） |
| `xref` | — | 反向交叉引用（callers/field-refs/type-refs），见 `dex-xref` |
| `search` | `find` | 指令模式搜索（--opcode/--class/--method），见 `dex-search` |
| `diff` | — | 两个 dex/apk 的语义（opcode 级）差异，见 `dex-diff` |
| `fingerprint` | `fp` | opcode 指纹与库/克隆识别，见 `dex-fingerprint` |
| `unlock` | — | 批量改访问标志（publicize/definalize），见 `dex-transform` |
| `replace` | — | 批量替换字符串常量，见 `dex-transform` |
| `strip-debug` | — | 清除全部调试信息，见 `dex-transform` |
| `patch` | — | 强制方法返回定值（绕过校验），见 `dex-transform` |
| `callgraph` | — | 导出方法级调用图（json/dot/mermaid），见 `dex-transform` |

### baksmali list 子命令

list 子命令（`classes`/`methods`/`strings`/`fields`/`types`）**默认输出 JSON**（机器可读），
`--format text` 切回人读文本；另支持 `--count`（仅总数）、`--group-by class`（按定义类分组计数），
详见 `dex-list-methods`。注意 `list dex`/`vtables`/`fieldoffsets`/`dependencies` 为纯文本输出，
无 `--format` 选项。

| 子命令 | 别名 | 列举内容 | Skill |
|--------|------|---------|-------|
| `strings` | `s` | 字符串表 | `dex-list-strings` |
| `methods` | `m` | 方法表 | `dex-list-methods` |
| `fields` | `f` | 字段表 | `dex-list-classes` |
| `types` | `t` | 类型表 | `dex-list-classes` |
| `classes` | `c` | 类列表 | `dex-list-classes` |
| `dex` | `d` | APK/OAT 中 dex 条目 | `dex-list-structure` |
| `vtables` | `v` | 虚方法表 | `dex-list-structure` |
| `fieldoffsets` | `fo` | 字段偏移 | `dex-list-structure` |
| `dependencies` | `deps` | odex/oat 依赖 | `dex-list-structure` |
