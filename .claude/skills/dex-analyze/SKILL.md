---
name: dex-analyze
description: "Use when the user asks to: (1) analyze dex bytecode with register type inference, (2) trace register types through method instructions, (3) verify or understand type flow in smali code, (4) debug deodex type resolution issues. Triggers: analyze, 分析, register types, 类型推断, type inference, register info, MethodAnalyzer."
---

# dex-analyze — dex 字节码分析（寄存器类型推断）

利用 dexlib2 的分析引擎对 dex 字节码进行寄存器类型推断和方法分析。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 通过 CLI 使用

分析能力主要通过 baksmali 的 `--register-info` 和 `--normalize-virtual-methods` 选项暴露：

```bash
# 寄存器类型推断（最常用）
java -jar baksmali.jar d -o out -r ALL app.apk

# 查看指令前寄存器类型
java -jar baksmali.jar d -o out -r ALLPRE app.apk

# 查看指令后寄存器类型
java -jar baksmali.jar d -o out -r ALLPOST app.apk

# 仅查看参数和目标寄存器
java -jar baksmali.jar d -o out -r ARGS,DEST app.apk

# 合并点完整类型集（调试分支合并）
java -jar baksmali.jar d -o out -r MERGE,FULLMERGE app.apk

# 虚方法归一化
java -jar baksmali.jar d -o out --normalize-virtual-methods app.apk
```

## 寄存器信息类型详解

| 标志 | 含义 | 用途 |
|------|------|------|
| `ALL` | 指令前+后的所有寄存器类型 | 全面分析 |
| `ALLPRE` | 指令执行前每个寄存器的类型 | 理解数据流 |
| `ALLPOST` | 指令执行后每个寄存器的类型 | 理解结果 |
| `ARGS` | 方法调用的参数寄存器 | 追踪调用参数 |
| `DEST` | 指令目标寄存器类型 | 快速查看赋值 |
| `MERGE` | 控制流合并点的寄存器类型（摘要） | 理解分支汇合 |
| `FULLMERGE` | 合并点的完整类型集 | 调试类型冲突 |

可组合使用：`-r ARGS,DEST,MERGE`

## 输出格式

寄存器类型信息以注释形式插入 smali 输出中：

```smali
# 指令前的寄存器状态
# v0=Ljava/lang/String; v1=I
const/4 v1, 0x0
# 指令后的寄存器状态
# v0=Ljava/lang/String; v1=I
```

## 通过 dexlib2 库使用（编程方式）

对于需要深度分析的场景，可直接使用 dexlib2 的 `analysis` 包：

```java
import org.jf.dexlib2.analysis.MethodAnalyzer;
import org.jf.dexlib2.analysis.ClassPath;
import org.jf.dexlib2.analysis.AnalyzedInstruction;

// 构建类路径
ClassPath classPath = ClassPath.fromClassPath(
    classPathEntries,   // 引导类路径
    dexFile,            // 目标 dex
    opcodes,            // Opcodes 实例
    false               // 是否检查包私有访问
);

// 分析方法
MethodAnalyzer analyzer = new MethodAnalyzer(
    classPath,
    method,
    methodImpl,
    false               // 是否去 odex
);

// 遍历分析结果
for (AnalyzedInstruction insn : analyzer.getAnalyzedInstructions()) {
    // 获取指令前的寄存器类型
    RegisterType[] preTypes = insn.getPreInstructionRegisterType();
    // 获取指令后的寄存器类型
    RegisterType[] postTypes = insn.getPostInstructionRegisterType();
}
```

## 典型场景

### 理解混淆代码

```bash
# 带 full merge 信息的反汇编，帮助理解混淆后的类型流
java -jar baksmali.jar d -o out \
  -r ALL,FULLMERGE --sequential-labels \
  obfuscated.apk
```

### 调试去 odex 问题

```bash
# 去odex时如果出现 UnresolvedOdexInstruction，用 register info 定位问题
java -jar baksmali.jar d -o out \
  -r ALL,FULLMERGE --normalize-virtual-methods \
  app.odex
```

### 追踪特定方法的数据流

```bash
# 只反汇编目标类 + 全量寄存器信息
java -jar baksmali.jar d -o out \
  --classes Lcom/target/Class -r ALL \
  app.apk
```

## 分析引擎核心类

| 类 | 作用 |
|----|------|
| `MethodAnalyzer` | 方法级指令流分析 |
| `ClassPath` | 类路径解析与类型层次 |
| `RegisterType` | 寄存器类型格（Cat-Unknown/Null/Integer/Reference等） |
| `InlineMethodResolver` | 解析内联方法 |
| `OdexedFieldInstructionMapper` | odex 字段指令映射 |
