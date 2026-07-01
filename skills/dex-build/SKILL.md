---
name: dex-build
description: "Use when the user asks to: (1) build or create a new dex file from scratch with dexlib2, (2) construct method bodies with builder instructions, (3) write or serialize a dex file, (4) add classes, methods, fields to a new dex. Triggers: dexlib2 build, 构建dex, DexPool, DexBuilder, MutableMethodImplementation, BuilderInstruction, write dex, 写入dex, create dex, 创建dex, 从零构建."
---

# dex-build — 用 dexlib2 构建和写出 dex 文件

使用 dexlib2 Java API 从零构建 dex 文件或修改后写出。

## 前置条件

```bash
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar
```

## 构建新 dex 的两种方式

| 方式 | 类 | 适用场景 |
|------|-----|---------|
| **DexPool** | `writer.pool.DexPool` | 从零构建，推荐 |
| **DexBuilder** | `writer.builder.DexBuilder` | 从 builder 对象构建 |

## 使用 DexPool（推荐）

```java
import org.jf.dexlib2.writer.pool.DexPool;
import org.jf.dexlib2.immutable.*;

// 创建 DexPool（需要指定 Opcodes 版本）
Opcodes opcodes = Opcodes.forApi(28);
DexPool pool = new DexPool(opcodes);

// 构建不可变对象并 intern
ImmutableClassDef classDef = new ImmutableClassDef(
    "Lcom/example/Hello;",           // type
    AccessFlags.PUBLIC.getValue(),    // accessFlags
    "Ljava/lang/Object;",             // superclass
    null,  // interfaces (List<String>)
    null,  // sourceFile
    null,  // annotations (Set<Annotation>)
    null,  // fields (Set<Field>)
    methods,  // methods (Set<Method>)
    null   // directMethods (Set<Method>)
);

pool.internClassDef(classDef);

// 写出
pool.writeTo(new File("output.dex"));
```

### 构建不可变方法

```java
import org.jf.dexlib2.immutable.*;

ImmutableMethod method = new ImmutableMethod(
    "Lcom/example/Hello;",           // definingClass
    "main",                           // name
    ImmutableList.of(                 // parameters
        new ImmutableMethodParameter(
            "[Ljava/lang/String;",    // type
            null,                      // annotations
            null                       // name
        )
    ),
    "V",                              // returnType
    AccessFlags.PUBLIC.getValue() | AccessFlags.STATIC.getValue(),
    null,                              // annotations
    methodImplementation               // MethodImplementation
);
```

## 使用 Builder 构建方法体

```java
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.*;
import org.jf.dexlib2.immutable.reference.ImmutableStringReference;
import org.jf.dexlib2.immutable.reference.ImmutableMethodReference;

// 创建可变方法实现（指定寄存器数量）
MutableMethodImplementation impl = new MutableMethodImplementation(2);

// 添加指令
impl.addInstruction(new BuilderInstruction21c(
    Opcode.CONST_STRING,
    0,                                          // register
    new ImmutableStringReference("Hello")       // reference
));

impl.addInstruction(new BuilderInstruction35c(
    Opcode.INVOKE_VIRTUAL,
    2,                                          // registerCount
    0, 1,                                       // registers A, B
    0, 0, 0,                                    // registers C, D, E (unused)
    new ImmutableMethodReference(
        "Ljava/io/PrintStream;",
        "println",
        ImmutableList.of("Ljava/lang/String;"),
        "V"
    )
));

impl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
```

### 添加 try/catch

```java
// 使用标签索引添加 try 块
impl.addCatch(
    0,   // start label index
    1,   // end label index
    2    // handler label index
);

// 带异常类型的 handler
impl.addCatch(
    0,   // start
    1,   // end
    2,   // handler
    "Ljava/lang/Exception;"  // exception type
);
```

### 添加调试信息

```java
impl.addPrologue(0);  // 方法入口标记
impl.addEpilogue(5);  // 方法出口标记
impl.addLineNumber(1, 0);  // 代码行号 → 指令索引
```

## 写出 dex 文件

### 方式一：DexFileFactory（最简单）

```java
import org.jf.dexlib2.DexFileFactory;

// 直接写出 DexFile 接口对象
DexFileFactory.writeDexFile("output.dex", dexFile);
```

### 方式二：DexPool（从零构建）

```java
DexPool pool = new DexPool(opcodes);
// ... intern 数据 ...
pool.writeTo(new File("output.dex"));
```

### 方式三：DexBuilder（从 builder 对象）

```java
import org.jf.dexlib2.writer.builder.DexBuilder;

DexBuilder builder = new DexBuilder(opcodes);
// ... 构建数据 ...
builder.writeTo(new File("output.dex"));
```

## 完整示例：构建 Hello World

```java
import org.jf.dexlib2.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.immutable.reference.*;
import org.jf.dexlib2.builder.*;
import org.jf.dexlib2.builder.instruction.*;
import org.jf.dexlib2.writer.pool.DexPool;

Opcodes opcodes = Opcodes.forApi(15);
DexPool pool = new DexPool(opcodes);

// 构建方法体
MutableMethodImplementation impl = new MutableMethodImplementation(2);
impl.addInstruction(new BuilderInstruction21c(
    Opcode.CONST_STRING, 1,
    new ImmutableStringReference("Hello World!")
));
impl.addInstruction(new BuilderInstruction21c(
    Opcode.SGET_OBJECT, 0,
    new ImmutableFieldReference(
        "Ljava/lang/System;", "out", "Ljava/io/PrintStream;"
    )
));
impl.addInstruction(new BuilderInstruction35c(
    Opcode.INVOKE_VIRTUAL, 2, 0, 1, 0, 0, 0,
    new ImmutableMethodReference(
        "Ljava/io/PrintStream;", "println",
        ImmutableList.of("Ljava/lang/String;"), "V"
    )
));
impl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));

// 构建方法
ImmutableMethod method = new ImmutableMethod(
    "LHelloWorld;", "main",
    ImmutableList.of(new ImmutableMethodParameter("[Ljava/lang/String;", null, null)),
    "V",
    AccessFlags.PUBLIC.getValue() | AccessFlags.STATIC.getValue(),
    null, null, impl
);

// 构建类
ImmutableClassDef classDef = new ImmutableClassDef(
    "LHelloWorld;",
    AccessFlags.PUBLIC.getValue(),
    "Ljava/lang/Object;",
    null, null, null, null,
    ImmutableSet.of(method),
    null
);

pool.internClassDef(classDef);
pool.writeTo(new File("hello.dex"));
```
