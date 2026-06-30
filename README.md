# smali-skills

smali/baksmali — 一个面向 **AI Agent 集成**的 smali/baksmali 增强发行版。在 JesusFreke 原版
汇编器/反汇编器之上，补齐了**展示层与查询层**：JSON 输出、交叉引用、模式搜索、统计聚合，
以及一整套渐进式披露的 Skills 文档。

> smali/baksmali 是 Dalvik（Android 虚拟机）使用的 dex 二进制格式的汇编器/反汇编器。语法
> 松散地基于 Jasmin/dedexer，完整支持 dex 的全部功能（注解、调试信息、行号信息等）。smali
> 文本是 dex 二进制的**无损文本表示**——smali ⇄ dex 可 100% 往返。

## 三层架构

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 3 · Skills（渐进式披露 Markdown，面向 AI Agent）       │
│  .claude/skills/*/SKILL.md  ——  21 个细粒度技能 + 索引        │
├─────────────────────────────────────────────────────────────┤
│  Layer 2 · CLI（展示/查询层，本仓库增强重点）                 │
│  baksmali:  disassemble / list / xref / search / dump        │
│  smali:     assemble                                         │
├─────────────────────────────────────────────────────────────┤
│  Layer 1 · dexlib2（核心库，转换引擎）                        │
│  iface / dexbacked / immutable / builder / writer / analysis │
└─────────────────────────────────────────────────────────────┘
```

- **Layer 1（dexlib2）**：读写/修改 dex 的核心 Java 库，零拷贝解析、可变构造、池化写入、
  deodex 类型推断。本仓库扩展了版本映射至 dex 040 / API 30+。
- **Layer 2（CLI）**：原版只有纯文本转换输出，Agent 必须正则解析。本仓库新增 `--format json`、
  `xref`、`search`、`--count`/`--group-by`，让 Agent 能直接消费结构化结果。
- **Layer 3（Skills）**：21 个 SKILL.md，按「快速开始 / 进阶 / 专家」三层渐进披露，覆盖
  每个 CLI 能力与 dexlib2 用法，供 Agent 按需加载。

## 安装

```bash
git clone https://github.com/android-security-engineer/smali-skills.git
cd smali-skills
./gradlew build          # 编译、测试、构建 fat jar
```

构建产物（fat jar，含全部依赖）：

- `smali/build/libs/smali.jar`     —— 汇编器
- `baksmali/build/libs/baksmali.jar` —— 反汇编器/查询工具

便捷包装脚本（`java -jar` 的薄封装）：

```bash
scripts/smali    assemble ...        # 等价于 java -jar smali/build/libs/smali.jar assemble ...
scripts/baksmali disassemble ...     # 等价于 java -jar baksmali/build/libs/baksmali.jar disassemble ...
```

需要 Java 8+（源码目标）与 Java 11（推荐用于构建，CI 使用 Java 11）。

## CLI 速查

### 转换（smali ⇄ dex）

```bash
# 反汇编 dex → smali 文本
java -jar baksmali/build/libs/baksmali.jar disassemble app.apk -o out/

# 汇编 smali 文本 → dex
java -jar smali/build/libs/smali.jar assemble out/ -o app.dex
```

### 列举（list）—— 支持 `--format json` 与聚合

```bash
baksmali list classes  app.apk                       # 列出所有类
baksmali list methods  app.apk --format json         # 方法列表，JSON 输出
baksmali list strings  app.apk --count               # 仅输出字符串总数
baksmali list methods  app.apk --group-by class      # 按定义类分组计数
baksmali list methods  app.apk --group-by class --format json
```

JSON schema 示例（`list methods --format json`）：

```json
[{"class":"Lcom/Example;","name":"foo","parameters":["I"],"returnType":"V"}]
```

### 交叉引用（xref）—— 反向引用查询

```bash
baksmali xref callers   app.apk --target Lcom/Example;->foo()V      # 谁调用了 foo
baksmali xref field-refs app.apk --target Lcom/Example;->count:I    # 谁访问了字段 count
baksmali xref type-refs  app.apk --target Lcom/Example;             # 谁引用了该类型
baksmali xref callers   app.apk --target foo()V --format json       # 子串匹配 + JSON
```

### 模式搜索（search）

```bash
baksmali search app.apk --opcode const-string,invoke-virtual         # opcode 序列
baksmali search app.apk --opcode 'const-string,*,invoke-virtual'     # * 匹配任意单条
baksmali search app.apk --class 'Lcom/.*' --method onCreate          # 类/方法正则过滤
baksmali search app.apk --opcode invoke-virtual --format json
```

## Skills 索引

21 个技能位于 `.claude/skills/`，索引见 [`.claude/skills/smali-skills/SKILL.md`](.claude/skills/smali-skills/SKILL.md)。
按能力分组：

- **读取/结构**：`dex-read`、`dex-list-structure`、`dex-list-classes`、`dex-list-methods`、
  `dex-list-strings`、`dex-multidex`
- **查询**：`dex-xref`（交叉引用）、`dex-search`（指令模式搜索）
- **转换**：`dex-disassemble`、`dex-assemble`、`dex-roundtrip`、`dex-build`
- **分析**：`dex-dump`、`dex-analyze`、`dex-instructions`、`dex-classpath`、`dex-deodex`
- **改写**：`dex-rewrite-references`、`dex-rewrite-structure`
- **基础**：`smali-syntax`、`smali-skills`（总索引）

## 可运行示例

`examples/` 下含 smali 源码示例（HelloWorld、Interface、Enums、InvokeCustom 等），可端到端
跑通 assemble → disassemble → list → xref 闭环，详见
[`examples/scripts/`](examples/scripts/)。

## 与上游的关系

本仓库是 [JesusFreke/smali](https://github.com/JesusFreke/smali) 的 fork：

- `upstream` 远程跟踪原版；`.github/workflows/sync-upstream.yml` 自动同步上游变更。
- 所有增强均为**纯加法**，不改动既有命令的默认行为（`--format` 默认 `text`，向后兼容）。
- CI（`.github/workflows/ci.yml`）在 Java 11 + Gradle 6.8.2 上构建并测试。
- Release 工作流（`.github/workflows/release.yml`）在打 tag 时构建并发布 fat jar。

## 构建/测试

```bash
./gradlew build                                    # 全量：编译 + 全部测试 + fat jar
./gradlew :dexlib2:test                            # 单模块测试
./gradlew :baksmali:test --tests '*JsonOutputTest' # 单测试类
./gradlew :baksmali:fb                             # baksmali 快速构建（跳过测试与 javadoc）
```

版本号从 git HEAD 短哈希派生（如 `2.5.2-<hash>` 或 `-dirty`）；release 构建去掉后缀。

## 资源链接

- [官方 dex 字节码参考](https://source.android.com/devices/tech/dalvik/dalvik-bytecode.html)
- [寄存器（Registers）wiki](https://github.com/JesusFreke/smali/wiki/Registers)
- [类型/方法/字段 wiki](https://github.com/JesusFreke/smali/wiki/TypesMethodsAndFields)
- [官方 dex 格式参考](https://source.android.com/devices/tech/dalvik/dex-format.html)
- [上游 JesusFreke/smali](https://github.com/JesusFreke/smali)
