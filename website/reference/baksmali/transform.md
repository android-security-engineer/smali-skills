---
title: transform — 写回变换
description: baksmali transform 子包——unlock/replace/strip-debug/patch 的变换实现
outline: [2, 3]
---

# ✏️ transform — 写回变换

`baksmali/transform/` 子包实现写回 dex 的各种变换。每个变换类是一个 `DexFile` → `DexFile` 的纯函数式转换，通过 `DexRewriter` 惰性装饰 dexlib2 对象图。命令层 `DexTransformCommand`/`UnlockCommand` 等调用这些类。

## 变换类

| 类 | 对应命令 | 作用 |
|----|----------|------|
| `AccessFlagTransform` | `transform unlock` | 清除 `PRIVATE`/`FINAL`/`PROTECTED` 等位，提权 |
| `StringReplaceTransform` | `transform replace` | 替换字符串池中的字符串 |
| `StripDebugTransform` | `transform strip-debug` | 移除 debug 项 |
| `ForceReturnTransform` | `transform patch` | 强制方法返回常量 |

## 工作机制

```mermaid
flowchart LR
    IN[DexFile 输入] --> DR[DexRewriter<br/>惰性装饰器]
    DR --> T["变换类<br/>覆写对应 Rewriter"]
    T --> OUT["变换后 DexFile<br/>(仍零拷贝视图)"]
    OUT --> DP[DexPool.writeTo]
    DP --> RES[新 dex 文件]
    style DR fill:#fff3e0
    style DP fill:#e8f5e9
```

变换不立即物化整个 dex——通过 `DexRewriter` 装饰，访问到某条指令/字段时才按需改写。最终 `DexPool.writeTo` 序列化时遍历触发实际变换并写盘。这与 `dexlib2/rewriter` 的惰性设计一致。

## AccessFlagTransform

```java
// 概念
public int apply(int accessFlags) {
    // 清除 ACC_PRIVATE | ACC_FINAL | ACC_PROTECTED 等
    // 使原本不可达的成员可被外部调用/继承
}
public DexFile apply(@Nonnull DexFile in) { /* DexRewriter 装饰 */ }
```

定位（源码注释）：让原本不可达的成员可达，便于补丁/插桩代码调用，或允许类被继承。源码：`baksmali/src/main/java/org/jf/baksmali/transform/AccessFlagTransform.java`。

## StringReplaceTransform

替换字符串池条目。若新串长度不同，dexlib2 在写盘时自动重新分配 string_data，无需手动对齐。通过 `InstructionRewriter` 与 `EncodedValueRewriter` 两个钩子覆盖所有字符串引用点。

## StripDebugTransform

移除 `LineNumber`/`StartLocal`/`EndLocal` 等 debug 项，减小体积、反基于调试信息的检测。通过 `DebugItemRewriter` 钩子。

## ForceReturnTransform

把指定方法体替换为返回常量（如 `check()Z` 强制返回 `0x1`）。需定位方法、构造 `MutableMethodImplementation`、替换指令。源码：`ForceReturnTransform.java`。

## 输出报告

各 `transform *` 命令成功时打印一行结构化 JSON 报告（`output/TransformReport`）：

```json
{"command":"unlock","input":"app.apk","output":"out.apk","classesScanned":N,"fieldsUnlocked":M,"methodsUnlocked":K}
```

## 实战

```bash
java -jar baksmali.jar transform unlock app.apk -o out.apk
java -jar baksmali.jar transform replace app.apk --old a --new b -o out.apk
java -jar baksmali.jar transform strip-debug app.apk -o out.apk
java -jar baksmali.jar transform patch app.apk --method "Lc/A;->c()Z" --return-const 0x1 -o out.apk
```

## 延伸阅读

- [dexlib2 rewriter 层](../dexlib2/rewriter.md) — 变换的底层机制
- [DexRewriter 详解](../dexlib2/dex-rewriter.md)
- [output 子包](./output.md) — TransformReport
- [transform 命令](./commands/transform.md)
- [写回变换工作流](../../guide/transform.md)
- [dex-transform skill](../../skills/dex-transform.md)
