---
title: dexlib2 模块
description: dexlib2 核心库——读写/修改 dex 的 Java 库
outline: [2, 3]
---

# 🧱 dexlib2 模块

`dexlib2` 是 smali/baksmali 的核心库，读写/修改 dex 的 Java 库，零拷贝解析、可变构造、池化写入、deodex 类型推断。无依赖其它模块。

## 子包

| 包 | 文档 |
|----|------|
| iface | [总览](./iface.md) · [指令](./iface-instruction.md) · [格式](./iface-formats.md) · [引用](./iface-reference.md) · [编码值](./iface-value.md) · [调试](./iface-debug.md) |
| dexbacked | [总览](./dexbacked.md) · [原始结构](./dexbacked-raw.md) |
| immutable | [总览](./immutable.md) |
| builder | [总览](./builder.md) |
| writer | [总览](./writer.md) · [pool](./writer-pool.md) · [builder](./writer-builder.md) |
| rewriter | [总览](./rewriter.md) |
| analysis | [总览](./analysis.md) |
| formatter | [总览](./formatter.md) |
| base | [总览](./base.md) |
| util | [总览](./util.md) |

## 核心类

- [DexFileFactory](./dexfile-factory.md) — 解析入口
- [DexBackedDexFile](./dexbacked-dexfile.md) — 零拷贝 dex
- [ImmutableDexFile](./immutable-dexfile.md) — 内存化 dex
- [MutableMethodImplementation](./mutable-method-implementation.md) — 可变方法体
- [DexPool](./dex-pool.md) — 池化写入
- [DexWriter](./dex-writer.md) — 序列化编排
- [ClassPath](./classpath.md) — 类型层次
- [MethodAnalyzer](./method-analyzer.md) — 类型推断
- [Opcode](./opcode.md) / [Opcodes](./opcodes.md) / [VersionMap](./version-map.md)
- [DexRewriter](./dex-rewriter.md) / [RewriterModule](./rewriter-module.md)
- [OatFile](./oat-file.md) / [ZipDexContainer](./zip-dex-container.md) / [InstructionFactory](./instruction-factory.md)

## 延伸阅读

- [代码参考总览](../)
- [三层架构](../../guide/architecture.md)
- [内部原理](../../internals/)
