---
title: CLI 备忘单
description: 所有 smali/baksmali 命令的一页速查
outline: [2, 3]
---

# 📋 CLI 备忘单

所有命令的一页速查。`baksmali` = 反汇编/查询/变换，`smali` = 汇编/工具。

```bash
# 通用约定
java -jar baksmali.jar <command> [options] <input>      # 查询类默认 JSON
java -jar smali.jar    <command> [options]              # 汇编/工具
# 短别名：list=l, classes=c, xref=x, callers=c ...
```

## 🔄 转换

| 命令 | 作用 | 示例 |
|------|------|------|
| `baksmali disassemble` | dex → smali | `baksmali disassemble app.apk -o out/` |
| `smali assemble` | smali → dex | `smali assemble in.smali -o out.dex` |
| `baksmali dump` | 带注释十六进制转储 | `baksmali dump app.apk` |

## 📋 列举（默认 JSON）

| 命令 | 别名 | 输出 |
|------|------|------|
| `list classes` | `l c` | `[{type,superclass,accessFlags,...}]` |
| `list methods` | `l m` | `[{class,name,parameters,returnType}]` |
| `list strings` | `l s` | `[{string}]` |
| `list fields` | `l f` | `[{class,name,type}]` |
| `list types` | `l t` | `[{type}]` |
| `list dex` | `l d` | 多 dex 条目（纯文本） |
| `list vtables` | `l v` | 虚方法表（需类路径） |
| `list fieldoffsets` | `l fo` | 字段偏移（需类路径） |
| `list dependencies` | `l deps` | odex 依赖 |

聚合：`--count`（`{"count":N}`）、`--group-by class`（`[{group,count}]`）。
人读：`--format text`。

## 🔍 交叉引用

| 子命令 | 别名 | 查 |
|--------|------|-----|
| `xref callers` | `x c` | 谁调用了某方法 |
| `xref field-refs` | `x f` | 谁访问了某字段 |
| `xref type-refs` | `x t` | 谁引用了某类型 |

```bash
java -jar baksmali.jar xref callers app.apk --target "Lcom/Ex;->foo()V"
# 不指定 --target：全量反向索引
```

## 🎯 搜索

```bash
java -jar baksmali.jar search app.apk --opcode const-string,invoke-virtual
# 通配符
java -jar baksmali.jar search app.apk --opcode const-string,*,return-void
# 正则过滤引用文本
java -jar baksmali.jar search app.apk --opcode invoke-virtual --filter "loadClass"
```

## 📊 比较 / 指纹

```bash
java -jar baksmali.jar diff old.apk new.apk            # 语义差异（JSON）
java -jar baksmali.jar fingerprint app.apk             # opcode 指纹
java -jar baksmali.jar fingerprint app.apk --method "Lcom/Foo;->bar()V"
```

## ✏️ 写回变换（输出 JSON 报告）

| 命令 | 作用 |
|------|------|
| `transform unlock` | 提权：private/final → public |
| `transform replace` | `--old X --new Y` 改字符串 |
| `transform strip-debug` | 去调试信息 |
| `transform patch` | `--method ... --return-const N` 方法补丁 |
| `callgraph` | `--method ...` 调用图 |

```bash
java -jar baksmali.jar transform unlock app.apk -o out.apk
```

## 🧩 集成

| 命令 | 作用 |
|------|------|
| `smali lsp` | LSP 语言服务器（诊断/大纲/悬浮/格式化） |
| `smali format` | 格式化 smali 文件 |
| `smali lint` | 风格检查 |
| `baksmali mcp` | MCP 服务器（只读工具） |
| `baksmali deodex` | odex 去优化（`--deodex -b framework.jar`） |

## 通用选项

| 选项 | 适用于 | 作用 |
|------|--------|------|
| `--format json\|text` | 查询类 | 输出格式（默认 json） |
| `--count` | list | 计数 |
| `--group-by class` | list methods | 分组计数 |
| `-o <path>` | 转换/变换 | 输出路径 |
| `-b / --boot-class-path` | vtable/deodex | 框架类路径 |
| `--target <sig>` | xref | 查询目标 |

## 延伸阅读

- [CLI 概览](./)
- [各命令详解](./disassemble.md) 等
- [快速上手](../guide/quickstart.md)
- [安全分析工作流](../guide/security-analysis.md)
