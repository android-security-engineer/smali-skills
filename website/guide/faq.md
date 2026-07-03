---
title: 常见问题 FAQ
description: smali-skills 使用中的常见问题与解答
outline: [2, 3]
---

# ❓ 常见问题 FAQ

## 安装与运行

### Q: 报 `Unsupported class file major version`？

JDK 版本过低。smali-skills 需 JDK 11+（CI 测 11/17）。升级 JDK 或用提供的 Docker 镜像。

### Q: `baksmali.jar` 和 `smali.jar` 去哪下载？

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
curl -fsSL -o smali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/smali.jar
```

或自行构建：`./gradlew build`，产物在 `smali/build/libs/smali.jar`、`baksmali/build/libs/baksmali.jar`。

### Q: 命令太长，有短别名吗？

有。`list` = `l`，`list classes` = `l c`，`xref callers` = `x c` 等。见各命令文档。

## 输出格式

### Q: 怎么切回人读文本？

查询类命令默认 JSON，加 `--format text`：

```bash
java -jar baksmali.jar list methods app.apk --format text
```

### Q: JSON 输出怎么过滤/聚合？

用 `jq`：

```bash
java -jar baksmali.jar list methods app.apk | jq '.[] | select(.class|test("com/foo"))'
java -jar baksmali.jar list methods app.apk --count           # {"count":N}
java -jar baksmali.jar list methods app.apk --group-by class   # [{group,count}]
```

## 反汇编与汇编

### Q: 往返真的 100% 无损吗？

是的。dex → smali → dex 在字节码层面零差异（含注解、调试信息、try/catch）。`smali-integration-tests` 含往返测试。详见 [往返工作流](./roundtrip.md)。

### Q: 反汇编输出文件命名规则？

按 Java 包名映射：`Lcom/foo/Bar;` → `com/foo/Bar.smali`，内部类 `$` 保留。由 `util/ClassFileNameHandler` 决定。

### Q: 汇编报语法错误怎么办？

用 `smali lsp` 在编辑器里看诊断，或 `smali format --lint` 检查。错误信息含行列号。

## xref 与 search

### Q: `xref` 不指定 `--target` 会怎样？

列出该类型的所有目标及其引用点（全量反向索引）。指定 `--target` 则只查该目标。

### Q: `search --opcode` 的 `*` 是什么？

通配符，匹配任意单条指令。`const-string,*,invoke-virtual` = 装字符串后（中间任意一条）调用。`--filter` 正则过滤引用文本。

## transform

### Q: transform 改完的 dex 能正常运行吗？

能，dexlib2 重新分配索引与偏移，输出合法 dex。但改 access flag 可能影响 ART 的方法内联/校验——务必测试。

### Q: 字符串替换改变长度会有问题吗？

不会。dexlib2 自动重新分配 string_data，无需手动对齐。

## deodex 与类型推断

### Q: deodex 报 `UnresolvedOdexInstruction`？

缺少框架类路径。加 `--boot-class-path /system/framework/framework.jar`（可多个）。

### Q: `list vtables` / `list fieldoffsets` 输出为空？

同上，需类路径构建类型层次。

## 集成

### Q: Claude Code 插件怎么更新？

marketplace 跟随 git，重新 `marketplace update` 即可拉最新。

### Q: MCP 服务器暴露写回变换吗？

不暴露。MCP 只暴露只读工具，防止 Agent 误改 dex。写回用 CLI 或 Skills。

## 延伸阅读

- [安装指南](./install.md)
- [快速上手](./quickstart.md)
- [如何阅读源码](./reading-source.md)
- [这个工具解决了什么问题](./solved-problem.md)
