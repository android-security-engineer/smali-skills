---
name: dex-search
description: "Use when the user asks to: (1) search for opcode instruction patterns/sequences in dex, (2) find const-string followed by invoke-virtual, (3) match instruction sequences with wildcards, (4) filter methods/classes by regex, (5) locate bytecode patterns. Triggers: search, pattern search, opcode pattern, 指令序列搜索, opcode search, find instruction sequence, baksmali search, --opcode."
---

# dex-search — 指令模式搜索

在方法指令流中搜索连续的 opcode 模式（如 `const-string,invoke-virtual`），支持 `*` 通配符
和类/方法正则过滤。适合定位「加载字符串后立即调用」「new-instance 后调用构造函数」等
字节码模式。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 快速参考

```bash
# 单 opcode
java -jar baksmali.jar search app.apk --opcode invoke-virtual

# opcode 序列（逗号分隔，按顺序连续匹配）
java -jar baksmali.jar search app.apk --opcode const-string,invoke-virtual

# 通配符 *：匹配任意单条 opcode
java -jar baksmali.jar search app.apk --opcode "const-string,*,invoke-virtual"

# 类正则过滤（只搜匹配的类）
java -jar baksmali.jar search app.apk --class "Lcom/example/.*" --opcode invoke-virtual

# 方法名正则过滤
java -jar baksmali.jar search app.apk --method "onCreate" --opcode invoke-virtual

# JSON 输出
java -jar baksmali.jar search app.apk --opcode const-string,invoke-virtual --format json

# 不指定 --opcode：按 --class/--method 正则列举匹配的方法
java -jar baksmali.jar search app.apk --class "Lcom/.*" --method "onCreate"
```

## 输出格式

文本模式：每个匹配输出 `类->方法 @ offset`，后跟匹配的指令（缩进）：

```
Lcom/Example;->greet()V @ offset 0x2
  const-string "hello"
  invoke-virtual Ljava/lang/StringBuilder;->append(...)...
```

JSON 模式：

```json
[{"caller":"Lcom/Example;->greet()V","offset":"0x2","instructions":["const-string \"hello\"","invoke-virtual ..."]}]
```

`offset` 是匹配序列第一条指令在方法体内的字节偏移（hex）。

## 匹配规则

- **连续匹配**：模式中的每个 token 必须与方法中连续的指令一一对应。
- **`*` 通配**：匹配任意一条 opcode（占位）。例如 `const-string,*,return-void` 中 `*`
  匹配 `const-string` 与 `return-void` 之间的那条指令。
- **重叠匹配**：从每个起始位置都尝试，会报告重叠的匹配。
- **大小写不敏感**：opcode 名（`invoke-virtual`、`INVOKE-VIRTUAL` 等价）。
- **类/方法正则**：`--class`/`--method` 用 `find()`（部分匹配）；`--class` 作用于类型描述符，
  `--method` 作用于方法名。

## 典型场景

| 场景 | 命令 |
|------|------|
| 找日志调用点 | `--opcode "const-string,invoke-virtual"` + grep 日志 tag |
| 找字符串拼接 | `--opcode "new-instance,invoke-direct,const-string,invoke-virtual"` (StringBuilder) |
| 找反射调用 | `--opcode "invoke-virtual"` + `--method "invoke"` + `--class "Ljava/lang/reflect/.*"` |
| 找某类的所有方法 | `--class "Lcom/example/.*"`（无 --opcode） |
| 找入口方法 | `--method "main"` |

## 与 xref 的区别

- `search`：按 **opcode 模式** 正向搜索指令序列（找「什么样的指令」）。
- `xref`：按 **引用目标** 反向查询（找「谁调用了这个方法」）。见 [[dex-xref]]。

## 工作原理

`search` 遍历 `ClassDef → Method → Instruction`，对每个方法做滑动窗口子序列匹配。
详见 `org.jf.baksmali.PatternSearcher`。
