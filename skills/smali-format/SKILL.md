---
name: smali-format
description: "Use when the user wants to normalize, pretty-print, re-indent, or style-check hand-written or disassembled smali source files. Triggers: format smali, smali format, smali lint, pretty print smali, reindent, 格式化 smali, 缩进, 代码风格, 对齐, trailing whitespace, tabs, lint smali, style check, gofmt for smali, 统一风格, CI 风格检查, formatter, linter."
---

# smali-format — smali 源码格式化 + 风格检查

两个纯文本级子命令，把 smali 源码整理成规范风格，并在 CI 里挡住不规范提交：

| 子命令 | 作用 |
|--------|------|
| `smali format` | 按块嵌套深度重新缩进（每级 4 空格）、去行尾空白、Tab→空格、折叠多余空行、保证结尾恰好一个换行 |
| `smali lint` | 只报告不修改：行尾空白 / Tab 缩进 / 缩进非 4 的倍数 / 连续空行 / CRLF / 缺结尾换行，退出码 1 表示有问题 |

**核心保证**：格式化是**纯文本**的——不解析字节码、不改语义、**幂等**（`format(format(x)) == format(x)`）。`smali lint` 报告的每一条，`smali format` 都能修掉——两者是同一套风格的"检查端"和"修复端"。

## 前置条件

```bash
curl -fsSL -o smali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/smali.jar
```

## format：格式化

```bash
# 打印单个文件的格式化结果到 stdout（不改动文件）
java -jar smali.jar format A.smali

# 就地重写（可传目录，递归所有 .smali）
java -jar smali.jar format --write out/

# CI 模式：不改动，只要有文件未格式化就列出并以退出码 1 失败
java -jar smali.jar format --check out/
```

- 不带 `--write`/`--check` 时只接受**单个文件**，把结果打到 stdout（便于管道/diff）。
- `--write`（别名默认展开）就地重写，末尾打印 `N file(s) changed`。
- `--check` 适合 CI/pre-commit：打印需要格式化的文件路径，任一未格式化则退出 1。

## lint：风格检查

```bash
# 文本报告：path:line:col: [rule] message，有问题则退出码 1
java -jar smali.jar lint out/

# JSON 报告（喂给脚本/CI 注解）
java -jar smali.jar lint --format json out/
```

文本输出示例：

```
out/A.smali:5:13: [trailing-whitespace] line has trailing whitespace
out/A.smali:5:1: [tab-indentation] line is indented with a tab; use spaces
2 issue(s) across 1 file(s).
```

JSON 输出（`--format json`）每条含 `file/line/column/rule/severity/message`，行列均 **1 基**。

### 规则一览

| rule | 含义 |
|------|------|
| `trailing-whitespace` | 行尾有空格/Tab |
| `tab-indentation` | 用 Tab 缩进（应用空格） |
| `indentation` | 前导缩进宽度不是 4 的倍数 |
| `multiple-blank-lines` | 连续 ≥2 空行 |
| `carriage-return` | 行内含 CR（CRLF 行尾） |
| `final-newline` | 文件结尾无换行 |

## 缩进模型（format 怎么判断层级）

- **无条件块开启**：`.method`、`.annotation`、`.subannotation`、`.array-data`、`.packed-switch`、`.sparse-switch` —— 一定有配对的 `.end ...`，进入即 +1 层。
- **条件块开启**：`.field`、`.param` —— **可能**是单行（无 body）也可能带注解 body。用前向 lookahead 判断：若在方法边界/同类开启前遇到匹配的 `.end field`/`.end param`，才算块、才 +1 层。
- **单语句调试指令**：`.local` / `.end local` **不改变**层级（它们不是块分隔符）。
- `.end ...` 闭合：先 -1 层再输出该行，使闭合指令与开启指令对齐。

## 编辑器集成（LSP）

`smali lsp` 语言服务器已通告 `documentFormattingProvider: true`，`textDocument/formatting`
复用同一个 `SmaliFormatter`，返回覆盖全文的单个 `TextEdit`。已格式化的文档返回空编辑数组。
在编辑器里"Format Document"即走这条路径（见 `smali-lsp`）。

```
→ textDocument/formatting  ← [ { range:{start:{0,0},end:{N,0}}, newText:"…规范化后的整份文本…" } ]
```

## CI 用法

在 `smali lint --format json` 或 `smali format --check` 上挂一步即可挡下不规范提交：

```bash
java -jar smali.jar format --check src/ || {
  echo "run: java -jar smali.jar format --write src/"; exit 1;
}
```

## 真实示例

构造一个含三类风格问题的 smali（Tab 缩进 + 行尾空白 + 无结尾换行）：

```bash
printf '.class public LBad;\n.super Ljava/lang/Object;\n.method public static foo()V\n\t.registers 1\nconst/4 v0,0x0   \nreturn-void\n.end method' > bad.smali
# 注意：第4行用 Tab 缩进，第5行行尾有3个空格，文件末尾无换行
```

**lint 文本报告**（默认 `--format text`，有问题则退出码 1）：

```bash
java -jar smali.jar lint bad.smali
```

```
bad.smali:4:1: [tab-indentation] line is indented with a tab; use spaces
bad.smali:5:15: [trailing-whitespace] line has trailing whitespace
bad.smali:7:12: [final-newline] file does not end with a newline
3 issue(s) across 1 file(s).
```

**lint JSON 报告**（`--format json`，喂给 CI 注解/脚本）：

```json
[
  {"file":"bad.smali","line":4,"column":1,"rule":"tab-indentation","severity":"warning","message":"line is indented with a tab; use spaces"},
  {"file":"bad.smali","line":5,"column":15,"rule":"trailing-whitespace","severity":"warning","message":"line has trailing whitespace"},
  {"file":"bad.smali","line":7,"column":12,"rule":"final-newline","severity":"warning","message":"file does not end with a newline"}
]
```

**format 修复**（打印格式化结果到 stdout，对照可见 Tab→4 空格、行尾空白已去、补结尾换行）：

```bash
java -jar smali.jar format bad.smali
```

```smali
.class public LBad;
.super Ljava/lang/Object;
.method public static foo()V
    .registers 1
    const/4 v0,0x0
    return-void
.end method
```

修完再 lint 一次即 `0 issue(s)`、退出码 0——`format` 与 `lint` 是同一套风格的修复端/检查端。

## 实现要点（源码位置）

- `smali/src/main/java/org/jf/smali/format/SmaliFormatter.java` — 纯 `format(String)→String`，
  深度栈 + 开启/闭合集合 + 条件开启 lookahead。**无 I/O、幂等、可独立单测。**
- `smali/src/main/java/org/jf/smali/format/SmaliLinter.java` — 纯 `lint(String)→List<Issue>`，
  每条规则文本级可判定；`leadingIndentWidth` 与 formatter 共享（Tab 按 4 展开）。
- `smali/src/main/java/org/jf/smali/format/SmaliFiles.java` — 把路径展开成 `.smali` 文件
  （目录递归、排序，确定性）。
- `smali/src/main/java/org/jf/smali/SmaliFormatCommand.java` — `format` 子命令（`--write`/`--check`）。
- `smali/src/main/java/org/jf/smali/SmaliLintCommand.java` — `lint` 子命令（`--format text|json`）。
- LSP 接线见 `SmaliLanguageServer.formatting(...)`。

单元测试见 `smali/src/test/java/org/jf/smali/format/`（重缩进、嵌套注解/字段块、switch payload、
幂等、`formattedOutputIsLintClean` 不变式）和 `smali/src/test/java/org/jf/smali/lsp/`（LSP formatting）。

## 相关 Skills

- `smali-syntax` — 手写 smali 的语法参考（格式化不改语法，只调风格）
- `smali-lsp` — 同一 formatter 在编辑器里的实时"Format Document"
- `dex-disassemble` — 生成 `.smali` 文本（反汇编输出通常已规范，手工编辑后可再 format）
- `dex-assemble` — 把格式化后的 smali 汇编回 dex
