---
name: dex-references
description: "Use when the user asks to: (1) read or write dex files programmatically with dexlib2, (2) load dex/apk/oat/odex files, (3) build a new dex file from scratch, (4) access dex internal data model (classes, methods, fields, instructions), (5) write custom dex manipulation tools. Triggers: dexlib2, DexFileFactory, DexPool, DexBuilder, dex编程, dex库, dex API, 编程操作dex."
---

# dex-references — dexlib2 库编程参考

dexlib2 是 smali/baksmali 的核心库，提供完整的 dex 文件读写和操作 Java API。

## 前置条件

```bash
# 从 GitHub Release 安装
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/install.sh | bash

# 或从源码构建
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew :dexlib2:build -x test -x javadoc
# jar 位置: dexlib2/build/libs/dexlib2-<version>.jar

# Maven 依赖（如果用 Gradle）
# implementation 'org.smali:dexlib2:2.5.2'
```

## 分层架构

```
┌─────────────────────────────────────────────┐
│              iface/ (只读接口)                │  ← 所有操作的通用货币
│  DexFile, ClassDef, Method, Field, ...       │
├──────────┬──────────┬──────────┬────────────┤
│dexbacked/│immutable/│ builder/ │  writer/   │  ← 四种实现策略
│零拷贝读取 │内存态    │可变构建  │  序列化     │
└──────────┴──────────┴──────────┴────────────┘
```

## 加载 dex 文件

### 自动检测格式

```java
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

// 自动检测: dex → odex → oat → zip/apk
DexBackedDexFile dexFile = DexFileFactory.loadDexFile("app.apk", null);
```

### 加载多 dex 容器

```java
import org.jf.dexlib2.iface.MultiDexContainer;

// 加载为容器（支持多 dex）
MultiDexContainer<? extends DexBackedDexFile> container =
    DexFileFactory.loadDexContainer(new File("app.apk"), null);

// 列举所有 dex 条目
List<String> entries = container.getDexEntryNames();
// → ["classes.dex", "classes2.dex", ...]

// 加载特定条目
MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
    container.getEntry("classes2.dex");
DexBackedDexFile dex2 = entry.getDexFile();
```

### 加载特定 dex 条目

```java
// 路径后缀匹配（支持 OAT 长路径名）
MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
    DexFileFactory.loadDexEntry(
        new File("app.oat"),
        "classes2.dex",  // 匹配条目名
        false,            // false = 路径后缀匹配
        null              // opcodes
    );
```

### 支持的文件格式

| 格式 | 类 | 自动检测 |
|------|-----|---------|
| `.dex` | `DexBackedDexFile` | ✅ |
| `.odex` | `DexBackedOdexFile` | ✅ |
| `.oat` | `OatFile` | ✅（含 vdex） |
| `.apk`/`.zip` | `ZipDexContainer` | ✅ |
| `.cdex` | `CDexBackedDexFile` | ✅ |

## 读取 dex 数据

### 遍历类

```java
for (ClassDef classDef : dexFile.getClasses()) {
    String className = classDef.getType();        // "Lcom/example/Main;"
    String superClass = classDef.getSuperclass();  // "Ljava/lang/Object;"
    int accessFlags = classDef.getAccessFlags();   // 0x01 = public

    // 遍历字段
    for (Field field : classDef.getFields()) {
        String name = field.getName();
        String type = field.getType();
    }

    // 遍历方法
    for (Method method : classDef.getMethods()) {
        String name = method.getName();
        List<? extends MethodParameter> params = method.getParameters();
        String returnType = method.getReturnType();

        // 方法实现（abstract/native 方法为 null）
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            // 遍历指令
            for (Instruction insn : impl.getInstructions()) {
                Opcode opcode = insn.getOpcode();
                // 按类型访问指令细节
                if (insn instanceof OneRegisterInstruction) {
                    int reg = ((OneRegisterInstruction) insn).getRegister();
                }
            }

            // try 块
            for (TryBlock<? extends ExceptionHandler> tryBlock : impl.getTryBlocks()) {
                // ...
            }
        }
    }
}
```

### 遍历注解

```java
for (ClassDef classDef : dexFile.getClasses()) {
    // 类级注解
    for (Annotation annotation : classDef.getAnnotations()) {
        String visibility = annotation.getVisibility(); // runtime/build/system
        String type = annotation.getType();
        for (AnnotationElement element : annotation.getElements()) {
            String name = element.getName();
            EncodedValue value = element.getValue();
        }
    }
}
```

### 访问字符串表

```java
// DexBackedDexFile 的字符串是惰性读取的
int stringCount = dexFile.getStringCount(); // 仅 DexBackedDexFile
for (String str : dexFile.getStrings()) {
    // 遍历所有字符串
}
```

## 构建新的 dex 文件

### 使用 DexPool（推荐，从头构建）

```java
import org.jf.dexlib2.writer.pool.DexPool;
import org.jf.dexlib2.immutable.ImmutableClassDef;
import org.jf.dexlib2.immutable.ImmutableMethod;
import org.jf.dexlib2.immutable.ImmutableMethodImplementation;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.MutableMethodImplementation;

// 创建 DexPool
DexPool pool = new DexPool(opcodes);

// 添加类定义
ImmutableClassDef classDef = new ImmutableClassDef(
    "Lcom/example/Hello;",
    AccessFlags.PUBLIC.getValue(),
    "Ljava/lang/Object;",
    null,  // interfaces
    null,  // source
    null,  // annotations
    null,  // fields
    methods,
    null   // direct methods
);

pool.internClassDef(classDef);

// 写出
pool.writeTo(new File("output.dex"));
```

### 使用 Builder（构建方法体）

```java
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.*;

MutableMethodImplementation impl = new MutableMethodImplementation(2); // 2 registers

// 添加指令
impl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
impl.addInstruction(new BuilderInstruction21c(
    Opcode.CONST_STRING,
    0,                                          // register
    new ImmutableStringReference("Hello")       // reference
));

// 添加 try/catch
impl.addCatch(
    0,   // start label index
    1,   // end label index
    2    // handler label index
);

// 添加调试信息
impl.addPrologue(0);
```

## 写出 dex 文件

```java
// 方式1：DexFileFactory（最简单）
DexFileFactory.writeDexFile("output.dex", dexFile);

// 方式2：DexPool（从零构建）
DexPool pool = new DexPool(opcodes);
// ... intern 数据 ...
pool.writeTo(new File("output.dex"));

// 方式3：DexBuilder（从 builder 对象）
DexBuilder builder = new DexBuilder(opcodes);
// ... 构建数据 ...
builder.writeTo(new File("output.dex"));
```

## 指令类型速查

```java
// 按指令格式访问（常见类型）
if (insn instanceof OneRegisterInstruction)       // vAA
if (insn instanceof TwoRegisterInstruction)       // vA, vB
if (insn instanceof ThreeRegisterInstruction)     // vA, vB, vC
if (insn instanceof ReferenceInstruction)         // method/field/type ref
if (insn instanceof NarrowLiteralInstruction)     // const/4, const/16, const
if (insn instanceof WideLiteralInstruction)       // const-wide
if (insn instanceof OffsetInstruction)            // goto, if-*, invoke-*
if (insn instanceof SwitchPayload)                // sparse-switch, packed-switch

// 获取指令格式
InstructionFormat format = insn.getOpcode().format;
```

## Opcode 与版本支持

```java
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.VersionMap;

// 获取特定 API 级别的操作码集
Opcodes opcodes = Opcodes.forApi(28);

// 检查操作码是否在特定版本可用
Opcode opcode = Opcode.INVOKE_CUSTOM;
boolean available = opcodes.isOpcodeSupported(opcode);

// dex 版本 → API 级别映射
int apiLevel = VersionMap.mapDexVersionToApi(35);  // dex 035 → API 15
```

## 工具类

```java
import org.jf.dexlib2.util.*;

// 引用工具
ReferenceUtil.getMethodDescriptor(methodRef);
ReferenceUtil.getFieldDescriptor(fieldRef);

// 类型工具
TypeUtils.isWideType("D");        // true (double)
TypeUtils.isPrimitiveType("I");   // true (int)

// 方法工具
MethodUtil.getShortMethodDescriptor(methodRef);

// 前置条件校验
Preconditions.checkValueArg(0, 3);  // 值范围检查

// 合成访问器解析
SyntheticAccessorResolver resolver = new SyntheticAccessorResolver(opcodes, classes);
```
