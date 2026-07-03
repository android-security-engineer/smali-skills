---
title: 内部原理
description: DEX 格式、opcode、版本映射、解析与写入机制等底层原理
outline: [2, 3]
---

# 🧬 内部原理

理解 smali-skills 背后的机制。这些文档面向想深入理解 dex 格式与工具实现的使用者。

```mermaid
mindmap
  root((内部原理))
    格式
      DEX 文件格式
      MUTF-8 字符串
      code_item 结构
      访问标志
    指令
      Opcode 参考
      指令格式
      版本映射
    机制
      零拷贝解析
      池化写入
      类型推断
      deodex 机制
    容器
      多 dex
      CDex 压缩
      OAT/odex
```

## 📐 格式与编码

| 文档 | 内容 |
|------|------|
| [DEX 文件格式](./dex-format.md) | 文件头、区段、map_list、code_item |
| [smali 语法参考](./smali-syntax.md) | 类型描述符、寄存器、方法体、注解 |
| [Opcode 参考](./opcodes.md) | 完整 Dalvik opcode 表与格式 |
| [版本映射](./version-map.md) | dex 版本 ↔ API ↔ opcode 集合 |

## ⚙️ 核心机制

| 文档 | 内容 |
|------|------|
| [零拷贝解析](./zero-copy.md) | dexbacked 如何惰性读取字节缓冲 |
| [池化写入](./pool-writing.md) | writer/pool 如何镜像 dex 池结构 |
| [类型推断](./type-inference.md) | analysis 如何推断寄存器类型 |
| [deodex 机制](./deodex.md) | 如何把 odex 指令还原为具体 invoke/field |

## 🚀 集成与部署

| 文档 | 内容 |
|------|------|
| [Claude Code 插件机制](./plugin.md) | marketplace.json 与 skill 自动发现 |
| [MCP 协议集成](./mcp.md) | baksmali mcp 暴露的工具 |
| [GitHub Pages 部署](./deployment.md) | CI/CD 流程 |
| [测试体系](./testing.md) | 往返测试、单元测试、e2e 冒烟 |

## 学习路径

```mermaid
flowchart LR
    A["🟢 新手"] --> B["读 DEX 文件格式"]
    B --> C["读 smali 语法参考"]
    C --> D["跑 quickstart"]
    D --> E["🟡 进阶<br/>Opcode 参考 + 版本映射"]
    E --> F["读三层架构"]
    F --> G["🔵 专家<br/>零拷贝 + 池化写入 + 类型推断"]
    style A fill:#e8f5e9
    style E fill:#fff3e0
    style G fill:#e3f2fd
```

建议按上述顺序学习，配合 [快速上手](../guide/quickstart.md) 与 [三层架构](../guide/architecture.md)。
