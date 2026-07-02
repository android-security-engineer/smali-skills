# smali format / lint

两个纯文本级子命令，把 smali 源码整理成规范风格，并在 CI 里挡住不规范提交。

```mermaid
flowchart LR
    SRC[.smali 文件/目录] --> FMT["format<br/>重新缩进 + 去行尾空白"]
    SRC --> LNT["lint<br/>只报告不修改"]
    FMT -->|幂等| OUT[规范 smali]
    LNT -->|同一套规则| ISS["issue 列表<br/>text 或 json"]

    style FMT fill:#e8f5e9
    style LNT fill:#fff3e0
```

| 子命令 | 作用 |
|--------|------|
| `smali format` | 按块嵌套深度重新缩进（每级 4 空格）、去行尾空白、Tab→空格、折叠多余空行、保证结尾恰好一个换行 |
| `smali lint` | 只报告不修改：行尾空白 / Tab 缩进 / 缩进非 4 的倍数 / 连续空行 / CRLF / 缺结尾换行，退出码 1 表示有问题 |

::: tip 核心保证
格式化是**纯文本**的——不解析字节码、不改语义、**幂等**（`format(format(x)) == format(x)`）。
`smali lint` 报告的每一条，`smali format` 都能修掉——两者是同一套风格的「检查端」和「修复端」。
:::

## format：格式化

```bash
# 打印单个文件的格式化结果到 stdout（不改动文件）
java -jar smali.jar format A.smali
# 就地重写（可传目录，递归所有 .smali）
java -jar smali.jar format --write out/
# CI 模式：不改动，只要有文件未格式化就列出并以退出码 1 失败
java -jar smali.jar format --check out/
```

- 不带 `--write`/`--check` 时只接受**单个文件**，把结果打到 stdout。
- `--write` 就地重写，末尾打印 `N file(s) changed`。
- `--check` 适合 CI/pre-commit：打印需要格式化的文件路径，任一未格式化则退出 1。

## lint：风格检查

```bash
# 文本报告（默认）：path:line:col: [rule] message
java -jar smali.jar lint out/
# JSON 报告（喂给脚本/CI 注解）
java -jar smali.jar lint --format json out/
```

::: warning 注意
`smali lint` 的 `--format` 默认是 **text**（JSON 是 opt-in）。这与 baksmali 查询命令相反——
lint 属 smali 侧，JSON 报告是显式开启的。
:::

每条 issue 含 `file/line/column/rule/severity/message`，行列均 **1 基**。

### 规则一览

| rule | 含义 |
|------|------|
| `trailing-whitespace` | 行尾有空格/Tab |
| `tab-indentation` | 用 Tab 缩进（应用空格） |
| `indentation` | 前导缩进宽度不是 4 的倍数 |
| `multiple-blank-lines` | 连续 ≥2 空行 |
| `carriage-return` | 行内含 CR（CRLF 行尾） |
| `final-newline` | 文件结尾无换行 |

## 真实示例

构造一个含三类风格问题的 smali（Tab 缩进 + 行尾空白 + 无结尾换行）：

```bash
printf '.class public LBad;\n.super Ljava/lang/Object;\n.method public static foo()V\n\t.registers 1\nconst/4 v0,0x0   \nreturn-void\n.end method' > bad.smali
# 第4行 Tab 缩进，第5行行尾 3 空格，文件末尾无换行
```

**lint 文本报告**（有问题则退出码 1）：

```bash
java -jar smali.jar lint bad.smali
```

```
bad.smali:4:1: [tab-indentation] line is indented with a tab; use spaces
bad.smali:5:15: [trailing-whitespace] line has trailing whitespace
bad.smali:7:12: [final-newline] file does not end with a newline
3 issue(s) across 1 file(s).
```

**lint JSON 报告**：

```json
[
  {"file":"bad.smali","line":4,"column":1,"rule":"tab-indentation","severity":"warning","message":"line is indented with a tab; use spaces"},
  {"file":"bad.smali","line":5,"column":15,"rule":"trailing-whitespace","severity":"warning","message":"line has trailing whitespace"},
  {"file":"bad.smali","line":7,"column":12,"rule":"final-newline","severity":"warning","message":"file does not end with a newline"}
]
```

**format 修复**（Tab→4 空格、行尾空白已去、补结尾换行）：

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

修完再 lint 一次即 `0 issue(s)`、退出码 0。

## CI 用法

```bash
java -jar smali.jar format --check src/ || {
  echo "run: java -jar smali.jar format --write src/"; exit 1;
}
```

## 编辑器集成

`smali lsp` 通告 `documentFormattingProvider: true`，`textDocument/formatting` 复用同一个 `SmaliFormatter`。
在编辑器里「Format Document」即走这条路径（见 [lsp](./lsp)）。
