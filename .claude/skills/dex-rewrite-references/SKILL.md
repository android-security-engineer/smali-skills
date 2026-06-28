---
name: dex-rewrite-references
description: "Use when the user asks to: (1) rename or remap types/classes in a dex file, (2) redirect method calls or field accesses, (3) obfuscate or deobfuscate class/method/field names, (4) apply systematic reference transformations to dex. Triggers: rename type, 重命名, remap method, 方法重定向, obfuscate, 混淆, deobfuscate, 反混淆, TypeRewriter, MethodReferenceRewriter, FieldReferenceRewriter, redirect call, API hook."
---

# dex-rewrite-references — 重命名和重映射 dex 引用

使用 dexlib2 rewriter 框架对 dex 文件中的类型名、方法引用和字段引用进行系统化变换。

## 前置条件

```bash
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar
```

## 核心概念

rewriter 框架拦截 dex 中的引用并替换为新的引用，**自动级联**：改了类型名，所有引用该类型的指令、注解、编码值都会自动更新。

## 重命名类型

```java
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.rewriter.*;

DexFile dexFile = DexFileFactory.loadDexFile("input.dex", null);

RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
        return type -> {
            // 简单映射表
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

### 使用映射表批量重命名

```java
Map<String, String> mapping = new HashMap<>();
mapping.put("Lcom/foo/Bar;", "Lcom/obfuscated/a;");
mapping.put("Lcom/foo/Baz;", "Lcom/obfuscated/b;");
// 从 mapping.txt 加载：
// Lcom/foo/Bar; -> Lcom/obfuscated/a;

RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<String> getTypeRewriter(Rewriters rewriters) {
        return type -> mapping.getOrDefault(type, type);
    }
};
```

## 重映射方法引用

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

### API 重定向（Hook 模式）

```java
// 将所有 Log.d() 调用重定向到自定义的 MyLog.d()
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters rewriters) {
        return ref -> {
            if (ref.getDefiningClass().equals("Landroid/util/Log;") &&
                ref.getName().equals("d")) {
                return new RewrittenMethodReference(ref) {
                    @Override public String getDefiningClass() { return "Lcom/myapp/MyLog;"; }
                };
            }
            return ref;
        };
    }
};
```

## 重映射字段引用

```java
RewriterModule module = new RewriterModule() {
    @Override
    public Rewriter<FieldReference> getFieldReferenceRewriter(Rewriters rewriters) {
        return ref -> {
            // 将字段引用重定向到另一个类
            if (ref.getDefiningClass().equals("LOldConfig;") &&
                ref.getName().equals("SERVER_URL")) {
                return new RewrittenFieldReference(ref) {
                    @Override public String getDefiningClass() { return "LNewConfig;"; }
                };
            }
            return ref;
        };
    }
};
```

## 组合多种变换

```java
RewriterModule module = new RewriterModule() {
    @Override public Rewriter<String> getTypeRewriter(Rewriters r) { ... }
    @Override public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters r) { ... }
    @Override public Rewriter<FieldReference> getFieldReferenceRewriter(Rewriters r) { ... }
};

DexFile rewritten = new DexRewriter(module).rewriteDexFile(dexFile);
```

## 完整变换流程

```java
// 1. 加载原始 dex
DexFile dexFile = DexFileFactory.loadDexFile("input.dex", null);

// 2. 定义变换
RewriterModule module = new RewriterModule() { /* ... */ };

// 3. 应用变换
DexRewriter rewriter = new DexRewriter(module);
DexFile rewritten = rewriter.rewriteDexFile(dexFile);

// 4. 写出结果
DexFileFactory.writeDexFile("output.dex", rewritten);
```

## 典型场景

| 场景 | 使用的 Rewriter |
|------|----------------|
| 类名混淆/反混淆 | `TypeRewriter` |
| API 重定向（hook） | `MethodReferenceRewriter` |
| 字段重命名/重定向 | `FieldReferenceRewriter` |
| 包名迁移 | `TypeRewriter` + `MethodReferenceRewriter` + `FieldReferenceRewriter` |

## 注意事项

- Rewriter 产生**新的不可变对象**，原始 dex 不受影响
- 类型重命名是级联的：改了类型名，所有引用该类型的指令、注解、编码值都会自动更新
- 写出时用 `DexFileFactory.writeDexFile()` 或 `DexPool.writeTo()`
