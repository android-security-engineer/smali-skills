---
name: dex-read
description: "Use when the user asks to: (1) load or read a dex/apk/odex/oat file programmatically with dexlib2, (2) traverse classes, methods, fields, annotations in a dex file, (3) access the string table or data model, (4) iterate instructions in a method body, (5) read dex file metadata. Triggers: dexlib2 read, 读取dex, load dex, DexFileFactory, DexBackedDexFile, traverse classes, 遍历类, read annotations, 读取注解, string table, 字符串表, dex数据模型."
---

# dex-read — 用 dexlib2 读取 dex 文件

使用 dexlib2 Java API 加载和遍历 dex/apk/odex/oat 文件中的数据。

## 前置条件

```bash
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar
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

## 遍历类

```java
for (ClassDef classDef : dexFile.getClasses()) {
    String className = classDef.getType();        // "Lcom/example/Main;"
    String superClass = classDef.getSuperclass();  // "Ljava/lang/Object;"
    int accessFlags = classDef.getAccessFlags();   // 0x01 = public
    String sourceFile = classDef.getSourceFile();  // "Main.java"

    // 遍历字段
    for (Field field : classDef.getFields()) {
        String name = field.getName();
        String type = field.getType();
        int flags = field.getAccessFlags();
    }

    // 遍历方法
    for (Method method : classDef.getMethods()) {
        String name = method.getName();
        List<? extends MethodParameter> params = method.getParameters();
        String returnType = method.getReturnType();

        // 方法实现（abstract/native 方法为 null）
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            // 见下方"遍历方法体"
        }
    }
}
```

## 遍历方法体

```java
MethodImplementation impl = method.getImplementation();

// 寄存器数量
int registerCount = impl.getRegisterCount();

// 遍历指令
for (Instruction insn : impl.getInstructions()) {
    Opcode opcode = insn.getOpcode();
    int format = insn.getOpcode().format;  // 指令格式

    // 按类型访问指令细节
    if (insn instanceof OneRegisterInstruction) {
        int reg = ((OneRegisterInstruction) insn).getRegister();
    }
    if (insn instanceof TwoRegisterInstruction) {
        int regA = ((TwoRegisterInstruction) insn).getRegisterA();
        int regB = ((TwoRegisterInstruction) insn).getRegisterB();
    }
    if (insn instanceof ReferenceInstruction) {
        Reference ref = ((ReferenceInstruction) insn).getReference();
    }
    if (insn instanceof NarrowLiteralInstruction) {
        int value = ((NarrowLiteralInstruction) insn).getNarrowLiteral();
    }
}

// try 块
for (TryBlock<? extends ExceptionHandler> tryBlock : impl.getTryBlocks()) {
    int start = tryBlock.getStartCodeAddress();
    int count = tryBlock.getCodeUnitCount();
    for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
        String type = handler.getExceptionType();  // null = catch-all
        int addr = handler.getHandlerCodeAddress();
    }
}

// 调试信息
for (DebugItem debugItem : impl.getDebugItems()) {
    if (debugItem instanceof LineNumber) {
        int line = ((LineNumber) debugItem).getLineNumber();
    }
    // 还有: LocalInfo, StartLocal, EndLocal, RestartLocal, PrologueEnd, EpilogueBegin, SetSourceFile
}
```

## 遍历注解

```java
for (ClassDef classDef : dexFile.getClasses()) {
    // 类级注解
    for (Annotation annotation : classDef.getAnnotations()) {
        int visibility = annotation.getVisibility();
        // 0=BUILD, 1=RUNTIME, 2=SYSTEM
        String type = annotation.getType();  // "Landroid/annotation/SuppressLint;"

        for (AnnotationElement element : annotation.getElements()) {
            String name = element.getName();
            EncodedValue value = element.getValue();
        }
    }

    // 字段/方法/参数注解通过 AnnotationDirectory 访问
}
```

## 访问字符串表

```java
// DexBackedDexFile 的字符串是惰性读取的
int stringCount = dexFile.getStringCount(); // 仅 DexBackedDexFile

for (String str : dexFile.getStrings()) {
    // 遍历所有字符串
}
```

## 工具类

```java
import org.jf.dexlib2.util.*;

// 引用描述
ReferenceUtil.getMethodDescriptor(methodRef);  // "Lcom/Example;->foo(I)V"
ReferenceUtil.getFieldDescriptor(fieldRef);    // "Lcom/Example;->bar:I"

// 类型判断
TypeUtils.isWideType("D");        // true (double)
TypeUtils.isPrimitiveType("I");   // true (int)

// 方法描述
MethodUtil.getShortMethodDescriptor(methodRef);
```
