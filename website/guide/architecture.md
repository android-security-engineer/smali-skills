# 三层架构

smali-skills 分为三层，自下而上：核心库 → CLI 工具 → Skills 文档，全部面向 AI Agent 集成设计。

```mermaid
flowchart TB
    subgraph L3["Layer 3 · Skills（渐进式披露 Markdown，面向 AI Agent）"]
        direction LR
        SK1["27 个 SKILL.md<br/>快速开始 / 进阶 / 专家"]
        SK2["真实命令→输出示例"]
    end
    subgraph L2["Layer 2 · CLI（展示/查询层）"]
        direction LR
        BAK["baksmali<br/>disassemble / list / xref<br/>search / diff / fingerprint<br/>变换 / mcp"]
        SMA["smali<br/>assemble / lsp<br/>format / lint"]
    end
    subgraph L1["Layer 1 · dexlib2（核心库，转换引擎）"]
        direction LR
        IF["iface<br/>只读接口"]
        DB["dexbacked<br/>零拷贝解析"]
        IM["immutable<br/>可变内存"]
        BD["builder<br/>方法体构造"]
        WR["writer<br/>池化写入"]
        AN["analysis<br/>deodex/类型推断"]
    end

    SK1 -->|shell 调用| BAK
    SK2 -->|shell 调用| SMA
    BAK --> L1
    SMA --> L1

    style L3 fill:#e8f5e9,stroke:#3c8d2c
    style L2 fill:#fff3e0,stroke:#ef6c00
    style L1 fill:#e3f2fd,stroke:#1565c0
```

## Layer 1 · dexlib2（核心库）

读写/修改 dex 的核心 Java 库，是整个项目最可复用、最常被改动的部分。无依赖其它模块。

```mermaid
flowchart LR
    A[(dex 文件)] -->|零拷贝| DB[dexbacked<br/>DexBackedDexFile]
    DB --> IF[iface<br/>DexFile/ClassDef/Method]
    IF -->|需要修改| IM[immutable<br/>内存化]
    IM --> BD[builder<br/>MutableMethodImplementation]
    BD --> WR[writer<br/>DexPool.writeTo]
    WR --> B[(新 dex)]
    AN[analysis<br/>ClassPath/MethodAnalyzer] -.->|deodex| DB

    style DB fill:#e3f2fd
    style WR fill:#e8f5e9
```

分层表示（`org.jf.dexlib2`）：

| 包 | 职责 |
|----|------|
| `iface/` | 只读接口：`DexFile`/`ClassDef`/`Method`/`Instruction`/引用/encoded 值/调试项。读写之间的通用货币。 |
| `dexbacked/` | 惰性、零拷贝实现，直接读原始 dex 字节缓冲。入口是 `DexFileFactory`，也处理 odex/oat 与 zip 容器。 |
| `immutable/` | 完全实例化的内存实现，用于独立持有/修改 dex。 |
| `builder/` | 可变方法体构造（`MutableMethodImplementation`、builder 指令、标签、try/catch）——smali tree walker 的目标。 |
| `writer/` | 把 iface 对象序列化回 dex；`DexWriter` 编排各 section writer。 |
| `rewriter/` | 通过覆写 `Rewriter` 变换 dex（重命名类型、重映射引用等）。 |
| `analysis/` | deodex 与类型推断（`MethodAnalyzer`/`ClassPath`/寄存器类型格）。 |

`Opcode.java` / `Opcodes.java` 与 `VersionMap.java` 编码每个 dex/API 版本支持哪些 opcode/格式。
本仓库已把版本映射扩展到 **dex 040 / API 30+**（hiddenapi 限制标志）。

## Layer 2 · CLI

原版只有纯文本转换输出，Agent 必须正则解析。本仓库新增 `--format json`（默认）、`xref`、
`search`、`--count`/`--group-by`，让 Agent 直接消费结构化结果。

```mermaid
flowchart LR
    subgraph 查询["查询类（默认 JSON）"]
        LIST["list classes/methods/strings/fields/types"]
        XREF["xref callers/field-refs/type-refs"]
        SRH["search --opcode"]
        DIF["diff OLD NEW"]
        FIN["fingerprint"]
    end
    subgraph 变换["写回变换类（默认 JSON 报告）"]
        UNL["unlock"]
        REP["replace"]
        STR["strip-debug"]
        PAT["patch"]
        CAL["callgraph"]
    end
    subgraph 转换["转换类"]
        DIS["disassemble"]
        ASM["assemble"]
        DMP["dump"]
    end

    查询 -->|只读| DEX[(dex/apk)]
    变换 -->|读+写| DEX
    转换 -->|读/写| DEX

    style 查询 fill:#e8f5e9
    style 变换 fill:#fff3e0
    style 转换 fill:#e3f2fd
```

查询类命令**默认输出 JSON**（机器可读）；变换类命令成功时打印一行结构化 JSON 报告（`command`/`input`/`output` + 各命令特有统计字段）。两者均可用 `--format text` 切回人读文本。

## Layer 3 · Skills

27 个 SKILL.md，按「快速开始 / 进阶 / 专家」三层渐进披露，覆盖每个 CLI 能力与 dexlib2 用法，
含真实命令→输出示例，供 Agent 按需加载。详见 [Skills 索引](../skills/)。
