---
title: baksmali search
description: 在方法指令流中按 opcode 模式与类/方法正则搜索，输出每个命中点的调用方法、偏移与匹配指令。
outline: [2, 3]
---

# 🔎 baksmali search

`baksmali search`（别名 `find`）对 dex 方法体做**正向指令模式搜索**：给定一个逗号分隔的 opcode 序列（如 `const-string,invoke-virtual`），在每条方法的指令流上做滑动窗口子序列匹配，报告 `类->方法 @ offset` 及命中的指令文本。与按引用目标反向查询的 `xref` 互补——`search` 回答"哪些指令序列出现了"，`xref` 回答"谁调用了这个方法"。

## 🛠️ 命令定位

- 命令名：`search`，别名 `find`（`@ExtendedParameters(commandName="search", commandAliases={"find"})`）
- 描述（`@Parameters(commandDescription)`）：`Search method instruction streams by opcode pattern and/or class/method regex.`
- 继承链：`SearchCommand` → `DexInputCommand` → `Command`（不依赖 `XrefCommand` / `ListCommand`）
- 搜索引擎：委托 `PatternSearcher`（同包）做滑动窗口匹配
- 三种选择器：`--opcode`（指令序列）/ `--class`（类描述符正则）/ `--method`（方法名正则）

源码：`baksmali/src/main/java/org/jf/baksmali/SearchCommand.java:71-99`；引擎在 `baksmali/src/main/java/org/jf/baksmali/PatternSearcher.java:57-191`。

## 📊 参数

`SearchCommand` 自有 `@Parameter` 加上委托的 `OutputFormatArguments` 与继承的 `DexInputCommand` 通用参数：

| 参数 | 说明 | 默认 | 必填 | arity | 来源 |
| --- | --- | --- | --- | --- | --- |
| `--opcode` | 逗号分隔 opcode 模式，如 `const-string,invoke-virtual`；`*` 匹配任意单条 opcode | 无 | 否 | 单值 | `SearchCommand.java:84-87` |
| `--class` | 限制到类型描述符匹配该正则的类（`find()` 部分匹配） | 无 | 否 | 单值 | `SearchCommand.java:89-91` |
| `--method` | 限制到方法名匹配该正则的方法（`find()` 部分匹配） | 无 | 否 | 单值 | `SearchCommand.java:93-95` |
| `--format` | 输出格式：`json`（默认，机器可读）或 `text`（人读）；未识别值回退 JSON | `json` | 否 | 单值 | `OutputFormatArguments.java:52-54` |
| `-a`, `--api` | dex 文件的数字 API level，用于选择 opcode 集 | `-1`（自动） | 否 | 单值 | `DexInputCommand.java:56-59` |
| `-h`, `-?`, `--help` | 显示用法信息 | false | 否 | 布尔 | `SearchCommand.java:77-79` |
| `file`（位置参数） | dex/apk/oat/odex 文件；多 dex 容器可用 `app.apk/classes2.dex` 路径语法 | — | 是 | 列表（取首项） | `DexInputCommand.java:61-65` |

注意：位置参数虽为 `List<String>`，但 `run()` 在 `SearchCommand.java:107-111` 校验 `inputList.size() > 1` 即报 `Too many files specified`，**仅接受单个 dex**。本命令**没有** `--boot-class-path`、`--deodex` 等分析参数——它只扫描原始字节码，不做类型解析。`--class`/`--method` 任一为空时编译为 `null`（`compile()` 见 `SearchCommand.java:224-230`），即"不过滤"。

## 🧬 主流程

```mermaid
flowchart TD
    A["run()<br/>SearchCommand:101"] --> B{help 或<br/>inputList 为空?}
    B -- 是 --> Z["usage() 退出"]
    B -- 否 --> C{inputList.size()>1?}
    C -- 是 --> Z
    C -- 否 --> D["loadDexFile(input)<br/>DexInputCommand:111"]
    D --> E["compile(classRegex/methodRegex)<br/>parsePattern(opcodePattern)"]
    E --> F{opcodes 为空?}
    F -- 是 --> G["listMatching:<br/>按 class/method 正则列举方法"]
    F -- 否 --> H["遍历 dexFile.getClasses()<br/>按 classPattern 过滤"]
    H --> I["PatternSearcher.search(classes,opcodes)<br/>滑动窗口子序列匹配"]
    I --> J["按 methodPattern 过滤命中<br/>(extractMethodName)"]
    J --> K{matches 为空?}
    K -- 是 --> L["输出 [] / stderr No matches found."]
    K -- 否 --> M{--format json?}
    M -- 是 --> N["renderJson:<br/>caller + offset + instructions[]"]
    M -- 否 --> P["renderText:<br/>caller @ offset + 缩进指令"]

    style E fill:#fff3e0
    style I fill:#e3f2fd
    style N fill:#e3f2fd
    style P fill:#e8f5e9
```

匹配引擎（`PatternSearcher.java:127-154`）：先把方法指令物化为 `List<Instruction>` 与累积 `codeUnits` 偏移表，再在每个起始位置 `start` 用 `matchesAt`（`PatternSearcher.java:156-169`）做窗口比对——token 为 `*` 跳过，否则 `token.equalsIgnoreCase(opcode.name)`。命中后**步进 1**继续找，因此重叠匹配也会报告。`offset` 即窗口首条指令的 `codeUnits` 偏移（hex）。

无 `--opcode` 时走 `listMatching`（`SearchCommand.java:159-195`）：纯按 `--class`/`--method` 正则列举匹配方法，JSON 输出 `{class, method, returnType}` 数组，文本输出 `类->方法`。

## ⚡ 典型用法

```bash
# 默认就是 JSON；下面不再重复 --format json
java -jar baksmali.jar search app.apk --opcode const-string,invoke-virtual
# 人读文本
java -jar baksmali.jar search app.apk --opcode const-string,invoke-virtual --format text
# 通配符 *：匹配任意单条 opcode
java -jar baksmali.jar search app.apk --opcode "const-string,*,invoke-virtual"
# 类/方法正则过滤
java -jar baksmali.jar search app.apk --class "Lcom/example/.*" --method "onCreate"
# 不指定 --opcode：按 --class/--method 正则列举匹配的方法
java -jar baksmali.jar search app.apk --class "Lcom/.*" --method "onCreate"
```

## 📤 真实命令 → 输出示例

用仓库自带的 `accessorTest.dex` fixture——内部类 `Accessors` 的每个方法在偏移 `0x2` 处 `invoke-static` 一个 `access$NNN` 合成桥接方法：

```bash
java -jar baksmali.jar search \
  dexlib2/src/test/resources/accessorTest.dex \
  --opcode invoke-static
```

默认 JSON 输出（节选，渲染逻辑见 `SearchCommand.java:207-222`）：

```json
[
  {"caller":"Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_and(Z)V","offset":"0x2","instructions":["invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$072(Lorg/jf/dexlib2/AccessorTypes;I)Z"]},
  {"caller":"Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_or(Z)V","offset":"0x2","instructions":["invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$076(Lorg/jf/dexlib2/AccessorTypes;I)Z"]}
]
```

人读文本对照（`--format text`，`SearchCommand.java:197-205`）：

```
Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_and(Z)V @ offset 0x2
  invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$072(Lorg/jf/dexlib2/AccessorTypes;I)Z
Lorg/jf/dexlib2/AccessorTypes$Accessors;->boolean_or(Z)V @ offset 0x2
  invoke-static Lorg/jf/dexlib2/AccessorTypes;->access$076(Lorg/jf/dexlib2/AccessorTypes;I)Z
```

无 `--opcode`、仅按正则列举方法的输出（`listMatching`，JSON）：

```json
[{"class":"Lcom/Example;","method":"onCreate","returnType":"V"}]
```

无匹配时 JSON 输出 `[]`，文本模式向 stderr 报 `No matches found.`（`SearchCommand.java:143-150`）；纯列举无匹配则报 `No matching classes/methods found.`（`SearchCommand.java:184-190`）。

## 🗺️ 典型场景

| 场景 | 命令 |
| --- | --- |
| 找日志调用点 | `--opcode "const-string,invoke-virtual"` 后 grep 日志 tag |
| 找字符串拼接 | `--opcode "new-instance,invoke-direct,const-string,invoke-virtual"`（StringBuilder） |
| 找反射调用 | `--opcode "invoke-virtual"` + `--method "invoke"` + `--class "Ljava/lang/reflect/.*"` |
| 找某类的所有方法 | `--class "Lcom/example/.*"`（无 `--opcode`） |
| 找入口方法 | `--method "main"`（无 `--opcode`） |

## 延伸阅读

- [../../../cli/search.md](../../../cli/search.md) — `search` 命令用户向导与场景速查
- [./xref-callers.md](./xref-callers.md) — 反向交叉引用：谁调用了指定方法（与 `search` 互补）
- [./list.md](./list.md) — 正向列举类/方法/字段/字符串
- ../../../../skills/dex-search — `dex-search` 技能：Agent 调用 search 的工作流
- ../../../../skills/dex-xref — `dex-xref` 技能：交叉引用查询工作流
