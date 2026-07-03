---
title: baksmali list vtables
description: 列出 dex 文件中每个类的虚方法分发表（vtable），基于类型层次推算继承与覆盖关系。
outline: [2, 3]
---

# 🧬 baksmali list vtables

列出 **dex 文件中每个类的虚方法分发表（virtual method table）**。vtable 是 ART/ART 风格 VM 在方法分派时使用的「槽位表」：把当前类及其所有父类的可覆盖方法（`public`/`protected`/包私有实例方法，非 `static`/`private`/`final`）按声明顺序铺平成一张索引表，子类覆盖父类方法时占用同一槽位。dex 本身**不存储** vtable，本命令通过构建 `ClassPath` 完成类型层次分析后推算得出，因此必须能解析到完整父类链（典型需 `--bootclasspath`）。

## 命令定位

- 命令名：`vtables`，别名 `vtable`、`v`（`@ExtendedParameters(commandName)`）
- 描述（`@Parameters(commandDescription)`）：`Lists the virtual method tables for classes in a dex file.`
- 继承：`DexInputCommand`（带 `-a/--api` 与位置输入），并 `@ParametersDelegate` 注入 `AnalysisArguments`（`-b/-c/-d` 类路径参数）与 `CheckPackagePrivateArgument`
- 输出：**纯文本**，无 `--format` 选项，逐类一段写入 `System.out`

源码：`baksmali/src/main/java/org/jf/baksmali/ListVtablesCommand.java:50-54`

## 参数

### 本命令自身参数

| 参数 | 说明 | 默认 | 必填 | arity |
| --- | --- | --- | --- | --- |
| `-h`, `-?`, `--help` | 显示用法信息（`help=true`） | false | 否 | 布尔 |
| `--classes` | 逗号分隔类列表，仅打印这些类的 vtable（`@ExtendedParameter(argumentNames="classes")`） | null | 否 | 列表 |
| `--override-oat-version` | 用指定 oat 版本的类路径分析，忽略文件实际 oat 版本；可用于把裸 dex 当作某版本 oat 来列 vtable | 0（不覆盖） | 否 | int |
| `file`（位置参数） | dex/apk/oat/odex 文件；apk/oat 多 dex 时可用 `app.apk/classes2.dex` 指定条目 | — | 是 | 列表（仅取首项） |

`--classes` 声明见 `ListVtablesCommand.java:66-69`；`--override-oat-version` 见 `:71-75`；多文件校验（>1 报 `Too many files specified`）见 `:87-91`。

### 继承自 `DexInputCommand` 的通用参数

| 参数 | 说明 | 默认 | 备注 |
| --- | --- | --- | --- |
| `-a`, `--api` | 目标文件的数字 API level | -1（自动） | 非 -1 时 `Opcodes.forApi` 构造指令集 |
| `file` | 位置输入（dex/apk/oat/odex） | — | 多 dex 容器按 `classes.dex`→首条目回退 |

源码：`baksmali/src/main/java/org/jf/baksmali/DexInputCommand.java:56-65`

### `AnalysisArguments` 与 `CheckPackagePrivateArgument`（类路径分析参数）

| 参数 | 说明 | 默认 | arity |
| --- | --- | --- | --- |
| `-b`, `--bootclasspath`, `--bcp` | 冒号分隔的 bootclasspath 文件列表；`--bootclasspath ""` 表示空 bcp | null（自动选择） | 列表 |
| `-c`, `--classpath`, `--cp` | 额外 classpath，追加在 bcp 之后 | `[]` | 列表 |
| `-d`, `--classpath-dir`, `--cpd`, `--dir` | 搜索 classpath 文件的目录，可多次指定 | null（取 dex 父目录） | 列表 |
| `--check-package-private-access`, `--package-private`, `--checkpp`, `--pp` | 计算 vtable 索引时启用包私有访问检查；oat 文件默认开启，4.2.0 odex 需手动开 | false | 布尔 |

源码：`baksmali/src/main/java/org/jf/baksmali/AnalysisArguments.java:54-83`

## 命令主流程

```mermaid
flowchart TD
    A[run] --> B{help 或 无输入?}
    B -- 是 --> U[usage 并返回]
    B -- 否 --> C{文件数 > 1?}
    C -- 是 --> E1[stderr: Too many files<br/>usage 并返回]
    C -- 否 --> D[loadDexFile input]
    D --> G[getOptions:<br/>loadClassPathForDexFile<br/>传入 oatVersion 与 checkPackagePrivateAccess]
    G --> H{classes 非空?}
    H -- 是 --> CL[对每个 cls 调<br/>classPath.getClass cls → ClassProto]
    H -- 否 --> L[遍历 dexFile.getClasses]
    L --> IF{是 interface?}
    IF -- 是 --> SKIP[跳过：接口无 vtable]
    IF -- 否 --> P[ClassProto = classPath.getClass classDef]
    CL --> V[listClassVtable]
    P --> V
    V --> M[methods = classProto.getVtable]
    M --> W1[写类头: Class <type> extends <super> : N methods]
    W1 --> W2[遍历 methods<br/>写 i:definingClass->name(params)retType]
    W2 --> NEXT{还有类?}
    NEXT -- 是 --> L
    SKIP --> NEXT
    NEXT -- 否 --> DONE[返回]

    style U fill:#fff3e0
    style E1 fill:#ffebee
    style G fill:#e3f2fd
    style M fill:#e8f5e9
    style DONE fill:#e8f5e9
```

核心在于把每个 `ClassDef` 升级为 `ClassProto`（`ListVtablesCommand.java:104`、`:111`），再调用 `getVtable()` 得到一个**已铺平的方法列表**（`:120`）：父类方法在前、被覆盖的方法在原槽位被替换为子类实现。`listClassVtable` 逐行打印 `index:definingClass->methodName(paramTypes)returnType`（`:127-131`）——其中 `definingClass` 可能是父类（继承未覆盖）也可能是当前类（覆盖）。`--override-oat-version` 经 `getOptions` 传入 `loadClassPathForDexFile`（`:147-148`），强制 `ClassPath` 用该 ART 版本的类型格。

## 典型用法与输出

```bash
# 基本用法（裸 dex 通常需 bcp 才能解析 java.lang.Object 等父类）
java -jar baksmali.jar list vtables -b /system/framework/framework.jar app.apk

# 短别名
java -jar baksmali.jar l v -b framework.jar app.apk

# 只看特定类的 vtable
java -jar baksmali.jar l v --classes Lcom/example/Main app.apk

# 指定 APK 内的某个 dex 条目
java -jar baksmali.jar l v -b framework.jar "app.apk/classes2.dex"

# 把裸 dex 当作 oat 版本 56 来列 vtable
java -jar baksmali.jar l v --override-oat-version 56 app.apk

# 分析 oat 时可直接传 boot.oat 作为 bcp
java -jar baksmali.jar l v -b boot.oat framework.oat
```

输出示例（每个类一段，类头含方法计数与父类，随后每行 `index:definingClass->name(params)retType`）：

```
Class Lcom/example/Main; extends Landroid/app/Activity; : 3 methods

0:Ljava/lang/Object;-><init>()V
1:Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V
2:Lcom/example/Main;->onCreate(Landroid/os/Bundle;)V
```

- 槽位 `0` 的 `<init>` 来自 `Object`，未被覆盖；槽位 `1`、`2` 是 `onCreate` 的继承槽与子类覆盖槽——子类 `Main.onCreate` 占用与父类 `Activity.onCreate` **相同的逻辑槽位**（这里展示为顺序追加，ART 在加载时会按签名匹配把覆盖塞进同一索引）。
- **接口类被跳过**：`AccessFlags.INTERFACE.isSet(...)` 为真即不打印（`:110`），因为接口的方法表由 IMT 处理而非 vtable。
- 若 `loadClassPathForDexFile` 抛异常，命令打印 `Error occurred while loading class path files.` 与栈踪、返回（`ListVtablesCommand.java:149-153`），不输出任何 vtable。

> 提示：vtable 是**推算值**——dex 不记录槽位表，ART 在类加载时按类型层次动态构建。要查看实例字段的对应布局，见 [`list fieldoffsets`](./list-fieldoffsets.md)。

## 源码要点

- 参数与构造：`ListVtablesCommand.java:50-79`
- 前置校验（help/空输入/多文件）：`:81-91`
- `getOptions()` 组装 `BaksmaliOptions` 并加载类路径（透传 `oatVersion` 与 `checkPackagePrivateAccess`）：`:137-156`
- `--classes` 分支与全量遍历分支（跳过 interface）：`:101-113`
- `listClassVtable` 写类头与方法行：`:119-135`
- 与 `list fieldoffsets` 共用 `AnalysisArguments`，但本命令**不**接受 `--format`、不写文件，仅写 stdout

## 延伸阅读

- [baksmali list](../../../cli/list.md) — list 家族总览（含 vtables 定位与纯文本输出说明）
- [list fieldoffsets](./list-fieldoffsets.md) — 对应的实例字段内存布局推算
- [list dependencies](./list-dependencies.md) — odex/oat 依赖列表，无需类路径分析
- [baksmali disassemble](../../../cli/disassemble.md) — `--boot-class-path` 参数的完整说明
- Skills: dex-list-structure — vtable/字段偏移/依赖提取的工作流
- Skills: dex-classpath — vtables 必须类路径的原因与构建
