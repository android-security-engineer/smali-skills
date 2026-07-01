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
│  .claude/skills/*/SKILL.md  ——  27 个细粒度技能 + 索引        │
├─────────────────────────────────────────────────────────────┤
│  Layer 2 · CLI（展示/查询层，本仓库增强重点）                 │
│  baksmali:  disassemble / list / xref / search / diff /      │
│             fingerprint / 变换 / mcp（MCP 服务器）           │
│  smali:     assemble / lsp（语言服务器）/ format / lint      │
├─────────────────────────────────────────────────────────────┤
│  Layer 1 · dexlib2（核心库，转换引擎）                        │
│  iface / dexbacked / immutable / builder / writer / analysis │
└─────────────────────────────────────────────────────────────┘
```

- **Layer 1（dexlib2）**：读写/修改 dex 的核心 Java 库，零拷贝解析、可变构造、池化写入、
  deodex 类型推断。本仓库扩展了版本映射至 dex 040 / API 30+。
- **Layer 2（CLI）**：原版只有纯文本转换输出，Agent 必须正则解析。本仓库新增 `--format json`、
  `xref`、`search`、`--count`/`--group-by`，让 Agent 能直接消费结构化结果。
- **Layer 3（Skills）**：27 个 SKILL.md，按「快速开始 / 进阶 / 专家」三层渐进披露，覆盖
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

### Homebrew（macOS / Linux）

```bash
brew tap android-security-engineer/tap
brew install smali-skills
# 之后可直接用 smali / baksmali 命令
baksmali list strings app.apk
```

Formula 见 [`packaging/homebrew/smali-skills.rb`](packaging/homebrew/smali-skills.rb)；
release 工作流会在每次打 tag 时自动回填版本号与 sha256 并推送到 tap 仓库。

### Docker（免装 JDK）

无需本地安装 JDK/Gradle，直接用容器运行：

```bash
# 拉取预构建镜像
docker pull ghcr.io/android-security-engineer/smali-skills:latest

# 反汇编（把当前目录挂进容器 /work）
docker run --rm -v "$PWD:/work" ghcr.io/android-security-engineer/smali-skills:latest \
  disassemble app.apk -o out/

# 汇编（切到 smali 入口）
docker run --rm -v "$PWD:/work" ghcr.io/android-security-engineer/smali-skills:latest \
  smali assemble out/ -o app.dex

# 查询（xref / search / list）
docker run --rm -v "$PWD:/work" ghcr.io/android-security-engineer/smali-skills:latest \
  xref callers app.apk --target 'Lcom/Example;->foo()V'
```

镜像 ENTRYPOINT 默认是 `baksmali`；如需 `smali`，把 `smali` 作为第一个参数（镜像内已装
`smali`/`baksmali` 两个包装脚本）。本地构建镜像：`docker build -t smali-skills .`。

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

### 语义差异（diff）—— opcode 级两文件比较

```bash
baksmali diff old.apk new.apk                 # 文本报告：+/- 类、~ 改动类及其方法
baksmali diff old.apk new.apk --format json   # 机器可读；退出码 0=一致 1=有差异
```

忽略寄存器分配/调试信息/偏移，只比 opcode 序列，详见 `dex-diff` skill。

### 指纹 / 库识别（fingerprint）—— 对重命名不敏感

```bash
baksmali fingerprint app.apk                          # 每个类一个 opcode 指纹哈希
baksmali fingerprint app.apk --level method           # 细到方法
baksmali fingerprint app.apk --match okhttp.dex --min-similarity 0.9   # 认出被混淆的库
```

基于 opcode 序列，改名/换寄存器不改变指纹；`--match` 用 n-gram 相似度做库/克隆识别，详见 `dex-fingerprint` skill。

### 写回变换（unlock / replace / strip-debug / patch / callgraph）

这组命令**读入 dex → 变换 → 写出新 dex**（`-o` 默认 `out.dex`，原文件不改），详见 `dex-transform` skill。

```bash
baksmali unlock      app.apk -o unlocked.dex                       # 全部 public + 去 final
baksmali replace     app.apk --from http://old --to http://new -o patched.dex   # 替换字符串常量
baksmali strip-debug app.apk -o stripped.dex                      # 清除行号/局部变量/参数名
baksmali patch       app.apk --method isPremium --return true -o patched.dex    # 强制方法返回定值
baksmali callgraph   app.apk --graph-format mermaid               # 导出调用图（json/dot/mermaid）
```

### 编辑器集成（smali lsp）

`smali.jar` 内置一个 **Language Server**（LSP over stdio，JSON-RPC）。编辑器/IDE 接入后可获得
实时诊断、类/方法/字段大纲、opcode 悬浮文档——无第三方依赖（在已有 Gson 上手写协议）。

```bash
java -jar smali/build/libs/smali.jar lsp     # 供编辑器客户端拉起
scripts/smali-lsp                            # 或用包装脚本（自动定位 smali.jar）
```

接入示例（Neovim / VS Code）与协议细节见 [`.claude/skills/smali-lsp/SKILL.md`](.claude/skills/smali-lsp/SKILL.md)。

### AI Agent 集成（baksmali mcp）

`baksmali.jar` 内置一个 **MCP（Model Context Protocol）服务器**（stdio 上的 JSON-RPC），把只读
dex 查询包装成 Agent 可直接调用的 **tools**：`list_dex` / `disassemble_class` / `search_opcodes` /
`xref`。支持 MCP 的宿主（Claude Desktop、IDE agent）接入后，Agent 无需 shell 出去再正则解析文本。

```bash
java -jar baksmali/build/libs/baksmali.jar mcp     # 供 MCP 宿主拉起
```

待查的 dex/apk 不在命令行给，而是每次 `tools/call` 用 `input` 参数传路径。Claude Desktop 接入
示例与协议细节见 [`.claude/skills/smali-mcp/SKILL.md`](.claude/skills/smali-mcp/SKILL.md)。

### 格式化 / 风格检查（smali format / lint）

`smali.jar` 内置纯文本级的格式化器与 linter：`format` 按块深度重缩进（每级 4 空格）、去行尾空白、
Tab→空格、折叠空行；`lint` 只报告不修改，退出码 1 表示有问题。格式化**不解析字节码、不改语义、
幂等**，`lint` 报告的每一条 `format` 都能修掉。

```bash
java -jar smali/build/libs/smali.jar format A.smali          # 打印到 stdout
java -jar smali/build/libs/smali.jar format --write out/     # 就地重写（目录递归）
java -jar smali/build/libs/smali.jar format --check out/     # CI：未格式化则退出 1
java -jar smali/build/libs/smali.jar lint --format json out/ # 风格报告（text|json）
```

编辑器里的"Format Document"复用同一 formatter（`smali lsp` 已通告 `documentFormattingProvider`）。
规则表与缩进模型见 [`.claude/skills/smali-format/SKILL.md`](.claude/skills/smali-format/SKILL.md)。

## 作为库依赖（dexlib2）

Layer 1 的 dexlib2/util 发布到 Maven Central（命名空间 `io.github.android-security-engineer`）：

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.android-security-engineer:dexlib2:2.5.2")
}
```

```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.github.android-security-engineer</groupId>
  <artifactId>dexlib2</artifactId>
  <version>2.5.2</version>
</dependency>
```

发布流程见 [`packaging/PUBLISHING.md`](packaging/PUBLISHING.md)。

## Skills 索引

27 个技能位于 `.claude/skills/`，索引见 [`.claude/skills/smali-skills/SKILL.md`](.claude/skills/smali-skills/SKILL.md)。
按能力分组：

- **读取/结构**：`dex-read`、`dex-list-structure`、`dex-list-classes`、`dex-list-methods`、
  `dex-list-strings`、`dex-multidex`
- **查询**：`dex-xref`（交叉引用）、`dex-search`（指令模式搜索）
- **比较**：`dex-diff`（两个 dex/apk 的语义差异）
- **指纹**：`dex-fingerprint`（opcode 指纹、库/克隆识别）
- **写回变换**：`dex-transform`（unlock/replace/strip-debug/patch/callgraph）
- **编辑器**：`smali-lsp`（LSP 语言服务器：诊断/大纲/悬浮）
- **格式化**：`smali-format`（format 格式化 + lint 风格检查，同一风格的修复端/检查端）
- **Agent 集成**：`smali-mcp`（MCP 服务器：把只读 dex 查询暴露为 Agent 工具）
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
- CI（`.github/workflows/ci.yml`）在 Java 11 + Gradle 8.14 上构建并测试。
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
