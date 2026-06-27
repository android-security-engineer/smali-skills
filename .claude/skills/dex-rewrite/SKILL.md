---
name: dex-rewrite
description: "Use when the user asks to: (1) rename types/classes in a dex file, (2) remap method or field references, (3) transform or obfuscate a dex file programmatically, (4) apply systematic modifications to dex metadata. Triggers: rewrite, 重写, transform, 变换, rename, 重命名, remap, obfuscate, 混淆, DexRewriter."
---

# dex-rewrite — dex 文件变换与重写

利用 dexlib2 的 rewriter 框架对 dex 文件进行系统化变换：重命名类型、重映射引用、修改注解等。

## 前置条件

```bash
# 从 GitHub Release 下载 dexlib2
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar
# 或从源码构建
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew :dexlib2:build -x test -x javadoc
```

## 核心概念

dexlib2 的 rewriter 框架允许你拦截和修改 dex 文件中的任何元素，而不需要手动操作字节。每个 rewriter 是一个函数：输入原始对象 → 输出修改后对象。

## Rewriter 体系

```java
import org.jf.dexlib2.rewriter.*;

// 核心接口：输入 T，输出 T（可以是修改后的版本）
public interface Rewriter<T> {
    T rewrite(T value);
}

// RewriterModule：打包一组重写器
// DexRewriter：对整个 dex 文件应用一个 module
```

### 可重写的元素

| Rewriter | 作用 |
|----------|------|
| `TypeRewriter` | 重命名类型（如混淆/反混淆类名） |
| `ClassDefRewriter` | 修改类定义 |
| `MethodRewriter` | 修改方法 |
| `FieldRewriter` | 修改字段 |
| `MethodReferenceRewriter` | 重映射方法引用 |
| `FieldReferenceRewriter` | 重映射字段引用 |
| `InstructionRewriter` | 修改指令 |
| `AnnotationRewriter` | 修改注解 |
| `EncodedValueRewriter` | 修改编码值 |
| `MethodImplementationRewriter` | 修改方法实现 |
| `TryBlockRewriter` | 修改 try 块 |
| `ExceptionHandlerRewriter` | 修改异常处理 |
| `DebugItemRewriter` | 修改调试信息 |
| `MethodParameterRewriter` | 修改方法参数 |
| `AnnotationElementRewriter` | 修改注解元素 |

## 常见变换示例

### 重命名类型

```java
// 将所有 "com/original/ClassName" 改为 "com/rewritten/NewName"
DexFile dexFile = DexFileFactory.loadDexFile("input.dex", null);

RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
        return type -> {
            if (type.equals("Lcom/original/ClassName;")) {
                return "Lcom/rewritten/NewName;";
            }
            return type;
        };
    }
};

DexFile rewritten = new DexRewriter(module).rewriteDexFile(dexFile);
DexFileFactory.writeDexFile("output.dex", rewritten);
```

### 重映射方法引用

```java
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters rewriters) {
        return ref -> {
            // 将所有对 OldClass.oldMethod 的调用重定向到 NewClass.newMethod
            if (ref.getDefiningClass().equals("LOldClass;") &&
                ref.getName().equals("oldMethod")) {
                return new RewrittenMethodReference(ref) {
                    @Override public String getDefiningClass() { return "LNewClass;"; }
                    @Override public String getName() { return "newMethod"; }
                };
            }
            return ref;
        };
    }
};
```

### 组合多种变换

```java
// 打包多个 rewriter 为一个 module
RewriterModule module = new RewriterModule() {
    @Override public Rewriter<String> getTypeRewriter(Rewriters rewriters) { ... }
    @Override public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters r) { ... }
    @Override public Rewriter<FieldReference> getFieldReferenceRewriter(Rewriters r) { ... }
};

DexFile rewritten = new DexRewriter(module).rewriteDexFile(dexFile);
```

## 完整变换流程

```java
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.rewriter.*;

// 1. 加载原始 dex
DexFile dexFile = DexFileFactory.loadDexFile("input.dex", null);

// 2. 定义变换
RewriterModule module = new RewriterModule() {
    // 按需重写 override
};

// 3. 应用变换
DexRewriter rewriter = new DexRewriter(module);
DexFile rewritten = rewriter.rewriteDexFile(dexFile);

// 4. 写出结果
DexFileFactory.writeDexFile("output.dex", rewritten);
```

## 典型场景

| 场景 | 使用的 Rewriter |
|------|----------------|
| 类名混淆/反混淆 | `TypeRewriter` + `ClassDefRewriter` |
| API 重定向（hook） | `MethodReferenceRewriter` + `FieldReferenceRewriter` |
| 注解修改/删除 | `AnnotationRewriter` + `AnnotationElementRewriter` |
| 调试信息清理 | `DebugItemRewriter` |
| 字段重命名 | `FieldReferenceRewriter` |
| 方法签名修改 | `MethodRewriter` + `MethodParameterRewriter` |

## 注意事项

- Rewriter 产生的是**新的不可变对象**，原始 dex 不受影响。
- 类型重命名是级联的：改了类型名，所有引用该类型的指令、注解、编码值都会自动更新。
- 写出时用 `DexFileFactory.writeDexFile()` 或 `DexPool.writeTo()`。
