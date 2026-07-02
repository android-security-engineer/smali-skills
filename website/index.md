---
layout: home

hero:
  name: smali-skills
  text: Android dex 字节码工具集
  tagline: 面向 AI Agent 的 smali/baksmali 增强发行版 — JSON 默认输出、交叉引用、模式搜索、写回变换、LSP/MCP
  image:
    src: /logo.svg
    alt: smali-skills
  actions:
    - theme: brand
      text: 快速上手
      link: /guide/quickstart
    - theme: alt
      text: 三层架构
      link: /guide/architecture
    - theme: alt
      text: GitHub
      link: https://github.com/android-security-engineer/smali-skills

features:
  - icon: 🤖
    title: JSON 默认输出
    details: 查询类命令（list/xref/search/diff/fingerprint）默认输出机器可读 JSON，AI Agent 与脚本可直接消费，--format text 切回人读文本。
  - icon: 🔄
    title: 无损往返
    details: smali ⇄ dex 100% 往返。反汇编、编辑、重新汇编，字节码层面零差异。
  - icon: 🔍
    title: 交叉引用与模式搜索
    details: 反向查询谁调用了某方法/访问了某字段；按 opcode 序列正搜指令模式（支持通配符与正则过滤）。
  - icon: ✏️
    title: 写回变换
    details: 一行命令完成 unlock/replace/strip-debug/patch——批量提权、改字符串、去调试、强制返回，输出结构化 JSON 报告。
  - icon: 🧩
    title: 渐进式披露 Skills
    details: 27 个 SKILL.md 按「快速开始 / 进阶 / 专家」三层组织，Agent 按需加载，含真实命令→输出示例。
  - icon: 📦
    title: Claude Code 插件
    details: 仓库即 marketplace，/plugin marketplace add 一键安装，技能自动发现，以 /smali-skills:&lt;skill&gt; 调用。
  - icon: 🖥️
    title: LSP 语言服务器
    details: smali lsp 提供 诊断 / 大纲 / 悬浮 / 格式化，Neovim、VS Code 即开即用。
  - icon: 🔌
    title: MCP 服务器
    details: baksmali mcp 把只读 dex 查询暴露为 Agent 工具，集成进任意 MCP 客户端。
---
