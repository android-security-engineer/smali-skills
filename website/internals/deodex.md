---
title: deodex 机制
description: 把 odex/oat 的 quick 指令还原为引用具体方法/字段的普通指令
outline: [2, 3]
---

# 🔄 deodex 机制

odex（optimized dex）与 oat 把运行时可解析的 `invoke-virtual`/字段访问预编译为带**偏移**的 quick 指令。这些指令脱离原设备无法解读，`baksmali` 的 deodex 流程把它们还原为引用具体方法/字段的普通指令。

详见 [类型推断与 deodex](./type-inference.md#deodex--odex-指令还原)——deodex 是类型推断的一个应用，复用 `MethodAnalyzer` + `ClassPath`。

## quick 指令族

| odex 指令 | 还原为 | 依据 |
|-----------|--------|------|
| `invoke-virtual-quick` | `invoke-virtual` | vtable 偏移 → 父类链定位方法 |
| `invoke-super-quick` | `invoke-super` | 同上 |
| `invoke-direct-quick` | `invoke-direct` | 方法偏移 |
| `iget-quick` / `iput-quick` | `iget` / `iput` | 字段偏移 → 类层次定位字段 |
| `sget-quick` / `sput-quick` | `sget` / `sput` | 静态字段偏移 |

## 还原流程

```mermaid
flowchart LR
    Q["quick 指令<br/>含 vtable/field 偏移 N"] --> CP["ClassPath<br/>解析对象寄存器类型"]
    CP --> HIER["遍历父类链<br/>第 N 个方法/字段"]
    HIER -> RES["具体 Method/Field id"]
    RES --> NOR["普通指令<br/>引用 id 而非偏移"]
    style Q fill:#ffcdd2
    style NOR fill:#c8e6c9
```

## 前置条件

deodex 需要完整的类型层次：目标类 + 所有父类/接口的字段与方法布局。框架类不在目标 dex 内，必须用 `--boot-class-path` 指定。

```bash
java -jar baksmali.jar disassemble app.odex --deodex \
  -b /system/framework/framework.jar -o out/
```

## 与 dump/list 的关系

- `baksmali dump` 触发类型推断，对 quick 指令标注其解析目标。
- `list vtables` / `list fieldoffsets` 列出 vtable/字段布局，需同样类路径。
- 脱离类路径时，quick 指令只能标注为 `UnresolvedOdexInstruction`（`Adaptors/Format/UnresolvedOdexInstructionMethodItem`）。

## 延伸阅读

- [类型推断与 deodex](./type-inference.md)
- [dexlib2 analysis 层](../reference/dexlib2/analysis.md)
- [ClassPath 详解](../reference/dexlib2/classpath.md)
- [dex-deodex skill](../skills/dex-deodex.md)
- [baksmali deodex 命令](../reference/baksmali/commands/deodex.md)
