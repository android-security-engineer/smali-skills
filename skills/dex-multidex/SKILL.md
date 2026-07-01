---
name: dex-multidex
description: "Use when the user asks to: (1) work with multi-dex APK files, (2) specify a particular dex entry in an APK or OAT, (3) list or access classes2.dex or other secondary dex files, (4) handle APK with multiple dex files. Triggers: multi-dex, 多dex, classes2.dex, multiple dex, dex entry, dex条目, APK多dex, specify dex, 指定dex, app.apk/classes2.dex."
---

# dex-multidex — 多 dex 文件处理

处理包含多个 dex 文件的 APK/OAT 容器：指定条目、列举、操作。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar
```

## CLI 中的多 dex 语法

所有 baksmali 命令都支持通过路径后缀指定特定 dex 条目：

```bash
# 基本语法：容器文件/条目名
java -jar baksmali.jar d -o out "app.apk/classes2.dex"
java -jar baksmali.jar l s "app.apk/classes2.dex"
java -jar baksmali.jar dump "app.apk/classes3.dex"
```

### 路径匹配规则

- `app.apk/classes2.dex` — 精确匹配条目名
- 支持部分路径匹配（OAT 文件的长路径名）
- 如有歧义，用双引号指定精确路径：`framework.oat/"/system/framework/blah.dex"`

### 查看容器中的 dex 条目

```bash
# 先列举 APK/OAT 包含哪些 dex
java -jar baksmali.jar l d app.apk
# 每行一个条目名，如：
# classes.dex
# classes2.dex

# 然后指定特定条目操作
java -jar baksmali.jar d -o out2 "app.apk/classes2.dex"
java -jar baksmali.jar l m "app.apk/classes2.dex"
```

## 默认行为

不指定条目时，默认处理第一个 dex（通常是 `classes.dex`）：

```bash
# 这两个等价
java -jar baksmali.jar d -o out app.apk
java -jar baksmali.jar d -o out "app.apk/classes.dex"
```

## 真实示例

把两个 fixture dex 打成一个多 dex APK，演示列举 + 指定条目：

```bash
# 造一个最小多 dex apk
cp dexlib2/src/test/resources/accessorTest.dex /tmp/classes.dex
cp baksmali/src/test/resources/LocalTest/classes.dex /tmp/classes2.dex
( cd /tmp && jar cf multidex.apk classes.dex classes2.dex )

# 1) 列举容器中的 dex 条目
java -jar baksmali.jar l d /tmp/multidex.apk
# classes.dex
# classes2.dex

# 2) 默认处理第一个 dex（classes.dex = accessorTest）
java -jar baksmali.jar l c /tmp/multidex.apk --format text
# Lorg/jf/dexlib2/AccessorTypes$Accessors;
# Lorg/jf/dexlib2/AccessorTypes;

# 3) 指定第二个 dex 条目
java -jar baksmali.jar l c "/tmp/multidex.apk/classes2.dex" --format text
# LLocalTest;
```

可见同一容器、不同条目，类集合完全不同——`classes2.dex`（LocalTest）与默认的 `classes.dex`（AccessorTypes）通过 `/条目名` 后缀精准区分。

## CLI 操作各 dex

### 反汇编特定 dex

```bash
# 反汇编第二个 dex
java -jar baksmali.jar d -o out2 "app.apk/classes2.dex"

# 反汇编所有 dex（循环处理）
for dex in $(java -jar baksmali.jar l d app.apk); do
    name=$(echo "$dex" | sed 's/\.dex$//')
    java -jar baksmali.jar d -o "out_$name" "app.apk/$dex"
done
```

### 列举各 dex 的信息

```bash
# 第二个 dex 的字符串
java -jar baksmali.jar l s "app.apk/classes2.dex"

# 第二个 dex 的方法
java -jar baksmali.jar l m "app.apk/classes2.dex"

# 第二个 dex 的类
java -jar baksmali.jar l c "app.apk/classes2.dex"
```

## 用 dexlib2 编程处理多 dex

### 加载多 dex 容器

```java
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;

// 加载为容器
MultiDexContainer<? extends DexBackedDexFile> container =
    DexFileFactory.loadDexContainer(new File("app.apk"), null);

// 列举所有 dex 条目名
List<String> entries = container.getDexEntryNames();
// → ["classes.dex", "classes2.dex", ...]

// 加载特定条目
MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
    container.getEntry("classes2.dex");
DexBackedDexFile dex2 = entry.getDexFile();
```

### 遍历所有 dex

```java
for (String entryName : container.getDexEntryNames()) {
    DexBackedDexFile dex = container.getEntry(entryName).getDexFile();
    System.out.println(entryName + ": " + dex.getClasses().size() + " classes");
}
```

### 加载特定 dex 条目（精确匹配）

```java
MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
    DexFileFactory.loadDexEntry(
        new File("app.oat"),
        "classes2.dex",
        false,  // false = 后缀匹配
        null    // opcodes
    );
```

## OAT 文件的多 dex

OAT 文件可能包含长路径名的 dex 条目：

```
/system/framework/framework.jar:classes.dex
/system/framework/framework.jar:classes2.dex
```

路径匹配支持部分匹配：

```bash
# 以下都可以匹配 framework.jar:classes2.dex
java -jar baksmali.jar l c "framework.oat/classes2.dex"
java -jar baksmali.jar l c "framework.oat/framework.jar:classes2.dex"
```

## 典型场景

| 场景 | 命令 |
|------|------|
| 确认 APK 是否多 dex | `java -jar baksmali.jar l d app.apk` |
| 反汇编第二个 dex | `java -jar baksmali.jar d -o out "app.apk/classes2.dex"` |
| 搜索所有 dex 中的字符串 | `for dex in ...; do java -jar baksmali.jar l s "app.apk/$dex"; done` |
| 批量反汇编所有 dex | 循环 `l d` + `d -o` |
