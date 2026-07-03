---
title: ZipDexContainer
description: 以 zip 容器视角承载 apk/jar 内多个 dex 文件的零拷贝读取入口。
outline: [2, 3]
---

# 📦 ZipDexContainer

`ZipDexContainer` 是 dexlib2 对「包含若干 `.dex` 条目的 zip 归档」（即 **APK / JAR**）的统一抽象。它实现 `MultiDexContainer<DexBackedDexFile>` 接口，把 zip 包里的每一个 dex 条目按需惰性装载为 `DexBackedDexFile`，是 `DexFileFactory` 处理多 dex 输入时的核心载体。

源码：`dexlib2/src/main/java/org/jf/dexlib2/dexbacked/ZipDexContainer.java`

## 🧩 角色定位

- zip 文件 ≠ 单个 dex：一个 APK 通常含 `classes.dex`、`classes2.dex`… 多个 dex，外加资源/签名条目。
- `ZipDexContainer` 只关心「**判断哪些条目是 dex**」并「**按名字取出对应 `DexEntry`**」，不负责反编译或写回。
- 它是**读取侧（dexbacked）**的容器实现，与 `OatFile`（odex/oat 容器）、`SingletonMultiDexContainer`（裸 dex 包装成单条目容器）并列，由 `DexFileFactory.loadDexContainer` 统一分发。

## 🗂️ 关键字段

| 字段 | 类型 | 作用 |
|---|---|---|
| `zipFilePath` | `File` | zip/apk 在磁盘上的路径，每次访问都重新打开 `ZipFile` |
| `opcodes` | `@Nullable Opcodes` | 指定 dex 版本对应的指令集；为 `null` 时由 `DexBackedDexFile` 从 dex 头自动推断 |

构造时只保存路径与 opcodes，**不持有任何打开的句柄**——这是它能在多线程 dump 中安全复用的关键（见 `ZipDexContainer.java:68`）。

## 🔍 关键方法

| 方法 | 作用 | 备注 |
|---|---|---|
| `getDexEntryNames()` | 遍历 zip 全部条目，返回其中「是 dex」的条目名列表 | 用 `isDex()` 逐条过滤；每次调用都新开/关闭一个 `ZipFile`（`:78`） |
| `getEntry(String)` | 按名字取单个 dex 条目，返回 `DexEntry` | 找不到返回 `null`；命中则整条读入内存为 `byte[]` 后构造 `DexBackedDexFile`（`:107`） |
| `isZipFile()` | 探测底层文件是否真的是 zip | 依据 `ZipFile` 构造是否抛 `IOException`；失败抛 `NotAZipFileException`（`:121`） |
| `isDex(ZipFile, ZipEntry)` | 用 `DexUtil.verifyDexHeader` 校验条目头部 magic | `protected`，子类可覆盖判定逻辑；非 dex 静默跳过（`:141`） |
| `getZipFile()` | 打开一个新 `ZipFile` | `protected`，失败转成 `NotAZipFileException`（`:157`） |
| `loadEntry(ZipFile, ZipEntry)` | 真正装载：读字节 → 构造匿名 `DexEntry` | `DexEntry.getDexFile()` 每次调用都**新建** `DexBackedDexFile(opcodes, buf)`，即零拷贝但按需物化（`:165`） |

## 📐 类协作关系

```mermaid
flowchart LR
    DF[DexFileFactory] -->|loadDexContainer / loadDexEntry / loadDexFile| ZDC[ZipDexContainer]
    ZDC -.->|implements| MDC[MultiDexContainer&lt;DexBackedDexFile&gt;]
    ZDC -->|getZipFile| ZF[java.util.zip.ZipFile]
    ZDC -->|isDex| DU[DexUtil.verifyDexHeader]
    ZDC -->|loadEntry → DexEntry| DE[DexEntry&lt;DexBackedDexFile&gt;]
    DE -->|getDexFile| DBDF[DexBackedDexFile opcodes, byte[]]
    DF -->|兜底: 裸 dex| SDC[SingletonMultiDexContainer]
    DF -->|兜底: oat| OF[OatFile]
    DEF[DexEntryFinder] -->|findEntry| DE
```

## 🔄 典型用法

直接通过 `DexFileFactory` 间接使用最常见（见 `DexFileFactory.java:234`、`:187`、`:87`）：

```java
// 1) 拿到整个容器，遍历每个 dex
MultiDexContainer<? extends DexBackedDexFile> container =
        DexFileFactory.loadDexContainer(new File("app.apk"), Opcodes.getDefault());
for (String name : container.getDexEntryNames()) {
    DexBackedDexFile dex = container.getEntry(name).getDexFile();
    // ...遍历 classes/methods
}

// 2) 只取一个具名条目（exactMatch=false 时支持 classes2.dex 模糊匹配）
MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry =
        DexFileFactory.loadDexEntry(new File("app.apk"), "classes", false, null);
```

直接构造 `ZipDexContainer` 也很轻量：

```java
ZipDexContainer zip = new ZipDexContainer(new File("app.apc"), opcodes);
if (zip.isZipFile()) {
    DexEntry<DexBackedDexFile> e = zip.getEntry("classes.dex");
}
```

## ⚙️ 源码要点

- **无状态句柄**：`loadEntry` 用 `ByteStreams.toByteArray` 把条目读成 `byte[]`（`:169`），随后立即关闭 `ZipFile`/`InputStream`；容器本身不缓存任何打开资源，故可被多个线程/多次调用复用。
- **`DexEntry` 是匿名类**（`:171`）：`getDexFile()` 每次都 `new DexBackedDexFile(opcodes, buf)`——`buf` 已在内存，`DexBackedDexFile` 本身又是零拷贝视图，因此重复调用代价很低。
- **判定非 dex 不抛错**：`isDex()` 把 `NotADexFile`/`InvalidFile`/`UnsupportedFile` 三种异常都吞掉返回 `false`（`:145`），保证 `getDexEntryNames()` 能跨过资源/签名等非 dex 条目。
- **非 zip 信号**：`getZipFile()` 把 `IOException` 统一转成 `NotAZipFileException`（`RuntimeException`），让 `DexFileFactory` 能用 `try/catch` 优雅回退到裸 dex / oat 分支（`DexFileFactory.java:89`）。
- **入口分发**：`DexFileFactory.loadDexContainer` 先构造 `ZipDexContainer` 并调 `isZipFile()`，命中即返回；否则回退到 `SingletonMultiDexContainer`（裸 dex/odex）或 `OatFile`（`DexFileFactory.java:240-256`）。

## 延伸阅读

- baksmali 多 dex dump：参考 `baksmali` CLI 的 `disassemble` 子命令文档
