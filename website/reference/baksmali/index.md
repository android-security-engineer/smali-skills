---
title: baksmali 模块
description: baksmali 反汇编/查询/变换/集成 CLI
outline: [2, 3]
---

# 🔧 baksmali 模块

`baksmali` 是反汇编器与查询/变换工具，依赖 dexlib2 + util。本仓库增强重点：JSON 默认输出、xref、search、transform、mcp。

## 子包

| 包 | 文档 |
|----|------|
| main | [入口与调度](./main.md) |
| Adaptors | [反汇编适配器](./adaptors.md) · [Debug](./adaptors-debug.md) · [Format](./adaptors-format.md) |
| transform | [写回变换](./transform.md) |
| output | [输出与报告](./output.md) |
| diff | [语义差异](./diff.md) |
| fingerprint | [opcode 指纹](./fingerprint.md) |
| graph | [调用图](./graph.md) |
| mcp | [MCP 服务器](./mcp.md) |
| formatter | [文本格式化](./formatter.md) |

## 命令

[disassemble](./commands/disassemble.md) · [dump](./commands/dump.md) · [list](./commands/list.md) · [xref](./commands/xref.md) · [search](./commands/search.md) · [diff](./commands/diff.md) · [fingerprint](./commands/fingerprint.md) · [transform](./commands/transform.md) · [mcp](./commands/mcp.md) · [deodex](./commands/deodex.md) 等

## 延伸阅读

- [代码参考总览](../)
- [三层架构](../../guide/architecture.md)
- [CLI 文档](../../cli/)
