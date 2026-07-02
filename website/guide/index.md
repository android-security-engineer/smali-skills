# 简介

<div class="vp-doc">

smali-skills 是一个面向 **AI Agent 集成** 的 smali/baksmali 增强发行版。在 JesusFreke 原版
汇编器/反汇编器之上，补齐了**展示层与查询层**：JSON 默认输出、交叉引用、模式搜索、统计聚合，
以及一整套渐进式披露的 Skills 文档。

</div>

> smali/baksmali 是 Dalvik（Android 虚拟机）使用的 dex 二进制格式的汇编器/反汇编器。语法
> 松散地基于 Jasmin/dedexer，完整支持 dex 的全部功能（注解、调试信息、行号信息等）。smali
> 文本是 dex 二进制的**无损文本表示**——smali ⇄ dex 可 100% 往返。

## 为什么需要它

原版 baksmali 只输出纯文本，AI Agent 必须写正则去解析「类列表」「方法签名」。本发行版把所有
**查询类命令**默认改成 JSON 输出，让 Agent 直接 `jq` / 结构化消费；同时补齐了交叉引用、模式
搜索、语义 diff、opcode 指纹、写回变换等能力，每一项都封装成一行 CLI，无需写 Java 代码。

## 它包含什么

- **CLI 层**：`baksmali`（disassemble / list / xref / search / diff / fingerprint / 变换 / mcp）
  与 `smali`（assemble / lsp / format / lint）。
- **库层**：`dexlib2`——读写/修改 dex 的核心 Java 库，零拷贝解析、可变构造、池化写入、deodex 类型推断。
- **文档层**：27 个 SKILL.md，按能力分组，含真实命令→输出示例。
- **生态**：作为 Claude Code 插件 + marketplace 一键安装；LSP/MCP 暴露给编辑器与 Agent。

## 下一步

- 了解 [三层架构](./architecture)
- [安装](./install) 工具链
- 跑通 [快速上手](./quickstart)
