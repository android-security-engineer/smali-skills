---
name: dex-rewrite-structure
description: "Use when the user asks to: (1) modify method implementations or instructions in a dex file, (2) rewrite annotations, debug info, or try/catch blocks, (3) transform class definitions, method parameters, or encoded values, (4) apply structural transformations to dex elements. Triggers: MethodImplementationRewriter, InstructionRewriter, AnnotationRewriter, DebugItemRewriter, TryBlockRewriter, ClassDefRewriter, 修改方法体, 重写指令, 修改注解, 修改调试信息, transform method, rewrite class."
---

# dex-rewrite-structure — 修改 dex 结构元素

使用 dexlib2 rewriter 框架对 dex 文件中的结构性元素（类定义、方法体、注解、调试信息等）进行变换。

## 前置条件

```bash
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar
```

## 可重写的结构元素

| Rewriter | 作用 | 常见场景 |
|----------|------|---------|
| `ClassDefRewriter` | 修改类定义 | 修改访问标志、超类、接口 |
| `MethodRewriter` | 修改方法 | 修改方法签名、访问标志 |
| `FieldRewriter` | 修改字段 | 修改字段类型、访问标志 |
| `MethodImplementationRewriter` | 修改方法实现 | 替换方法体、注入代码 |
| `InstructionRewriter` | 修改指令 | 替换特定指令 |
| `AnnotationRewriter` | 修改注解 | 添加/删除/修改注解 |
| `AnnotationElementRewriter` | 修改注解元素 | 修改注解参数值 |
| `EncodedValueRewriter` | 修改编码值 | 修改静态字段初始值 |
| `TryBlockRewriter` | 修改 try 块 | 修改异常捕获范围 |
| `ExceptionHandlerRewriter` | 修改异常处理 | 修改 catch 类型 |
| `DebugItemRewriter` | 修改调试信息 | 清除行号/局部变量信息 |
| `MethodParameterRewriter` | 修改方法参数 | 修改参数名/注解 |

## 修改方法实现

```java
import org.jf.dexlib2.rewriter.*;

RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<MethodImplementation> getMethodImplementationRewriter(Rewriters rewriters) {
        return impl -> {
            // 返回一个新的 MethodImplementation
            // 可以修改寄存器数、指令列表、try 块等
            return new RewrittenMethodImplementation(rewriters, impl) {
                // 按需 override
            };
        };
    }
};
```

## 修改指令

```java
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<Instruction> getInstructionRewriter(Rewriters rewriters) {
        return insn -> {
            // 替换特定指令
            if (insn.getOpcode() == Opcode.RETURN_VOID) {
                // 不能直接创建新 Instruction，需要通过 MethodImplementationRewriter
            }
            return insn;
        };
    }
};
```

> **注意**：指令重写通常需要配合 `MethodImplementationRewriter` 使用，因为指令属于方法体。

## 修改注解

```java
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<Annotation> getAnnotationRewriter(Rewriters rewriters) {
        return annotation -> {
            // 删除特定注解（返回 null 会导致问题，应返回空集合的过滤）
            if (annotation.getType().equals("Landroid/annotation/SuppressLint;")) {
                // 过滤掉此注解（在 ClassDef 层面过滤 annotations 集合更安全）
            }
            return annotation;
        };
    }
};
```

## 修改编码值（静态字段初始值）

```java
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<EncodedValue> getEncodedValueRewriter(Rewriters rewriters) {
        return value -> {
            // 替换字符串常量值
            if (value instanceof StringEncodedValue) {
                StringEncodedValue sv = (StringEncodedValue) value;
                if (sv.getValue().equals("old_url")) {
                    return new ImmutableStringEncodedValue("new_url");
                }
            }
            return value;
        };
    }
};
```

## 清除调试信息

```java
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<DebugItem> getDebugItemRewriter(Rewriters rewriters) {
        return item -> null;  // 返回 null 会删除所有调试项
    }
};
```

## 修改类定义

```java
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<ClassDef> getClassDefRewriter(Rewriters rewriters) {
        return classDef -> {
            return new RewrittenClassDef(rewriters, classDef) {
                // 修改访问标志
                @Override
                public int getAccessFlags() {
                    return classDef.getAccessFlags() & ~AccessFlags.FINAL.getValue();
                }
            };
        };
    }
};
```

## 组合引用重写 + 结构重写

```java
// 同时修改引用和结构
RewriterModule module = new RewriterModule() {
    @Override public Rewriter<String> getTypeRewriter(Rewriters r) { /* 重命名 */ }
    @Override public Rewriter<EncodedValue> getEncodedValueRewriter(Rewriters r) { /* 改值 */ }
    @Override public Rewriter<DebugItem> getDebugItemRewriter(Rewriters r) { /* 清除调试 */ }
};

DexFile rewritten = new DexRewriter(module).rewriteDexFile(dexFile);
DexFileFactory.writeDexFile("output.dex", rewritten);
```

## 注意事项

- `RewrittenXxx` 基类会自动委托到原始对象，只需 override 需要修改的方法
- 返回 null 并不总是安全的（某些集合不允许 null 元素），建议通过过滤集合实现删除
- 修改方法体（指令/寄存器）通常通过 `MutableMethodImplementation` 构建新方法体，而非通过 rewriter
