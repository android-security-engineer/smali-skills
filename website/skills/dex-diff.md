---
title: dex-diff — 两个 dex/apk 的语义差异
description: 用 baksmali diff 在 opcode 层面比较两个 dex/apk，过滤重编译噪声，报告新增/删除/改动的类与方法，可作为脚本门控的回归基线。
outline: [2, 3]
---

# 🔀 dex-diff — 两个 dex/apk 的语义差异

`baksmali diff OLD NEW` 在**操作码（opcode）层面**比较两个 dex/apk，输出新增/删除的类、以及同名类下新增/删除/**改动**的方法。方法被判为「改动」的依据是 opcode 序列不同——寄存器分配、调试信息（`.line`/`.local`/参数名）、指令偏移都被**刻意忽略**，所以「同源重编译产生的噪声」不会被误报为语义改动。这正是它区别于 `diff <(baksmali d a) <(baksmali d b)` 纯文本对比的地方。

## 前置条件

```bash
curl -fsSL -o baksmali.jar \
  https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
alias baksmali='java -jar baksmali.jar'
```

## 能力与工作流

```mermaid
flowchart LR
    OLD[(OLD dex/apk)] -->|DexFileFactory 加载| ODB[DexBackedDexFile]
    NEW[(NEW dex/apk)] -->|隔离二次加载| NDB[DexBackedDexFile]
    ODB --> KEY[以类型描述符为键<br/>方法以规范描述符为键]
    NDB --> KEY
    KEY --> CMP{逐类逐方法对比}
    CMP -->|NEW 独有类| AC[addedClasses]
    CMP -->|OLD 独有类| RC[removedClasses]
    CMP -->|同名类方法体变| CC[changedClasses<br/>added/removed/changedMethods]
    AC --> OUT{输出格式}
    RC --> OUT
    CC --> OUT
    OUT -->|--format text 默认 JSON? 否| TXT[人读文本]
    OUT -->|默认| JSON[JSON 报告]
    JSON -. jq .addedClasses .-> AUD[版本审计]
    TXT -. 脚本门控 .-> GATE[exit 0/1]

    style KEY fill:#fff3e0
    style CC fill:#e8f5e9
    style GATE fill:#fce4ec
```

核心是纯模型 `org.jf.baksmali.diff.DexDiff`（无 I/O）：`DexDiff.compute()` 在 `baksmali/src/main/java/org/jf/baksmali/diff/DexDiff.java:93` 完成类集合差集与逐类方法对比；`DiffCommand.run()` 在 `baksmali/src/main/java/org/jf/baksmali/DiffCommand.java:77` 负责加载、打印与退出码。方法归一化在 `opcodeSignature()`（`DexDiff.java:174`）：把方法体每条指令的 opcode 名逗号连接，abstract/native（无方法体）归一化为空串。

## 命令

```bash
# 默认就是 JSON（机器可读）；两个位置参数：OLD NEW
baksmali diff old.apk new.apk
# 要人读文本：显式 --format text
baksmali diff old.apk new.apk --format text
# 多 dex 条目语法（apk/oat 一致）
baksmali diff app.apk/classes2.dex app2.apk/classes2.dex
```

两个位置参数按顺序为 OLD、NEW。`--format` 由共享的 `OutputFormatArguments` 控制（`DiffCommand.java:70`）；`run()` 在 `:89`/`:92` 隔离加载 OLD/NEW，`:95` 调 `DexDiff.compute`，`:97`-`:101` 按 format 打印，`:103`-`:105` 在有差异时 `System.exit(1)`。

## 真实命令 → 输出

用仓库自带两个 fixture 对比——`accessorTest.dex`（含 `AccessorTypes` 两个类）与 `LocalTest/classes.dex`（含 `LLocalTest;`），类集合完全不重叠：

```bash
$ java -jar baksmali.jar diff \
    dexlib2/src/test/resources/accessorTest.dex \
    baksmali/src/test/resources/LocalTest/classes.dex
```

实际输出（默认 JSON）：

```json
{
  "addedClasses": ["LLocalTest;"],
  "removedClasses": [
    "Lorg/jf/dexlib2/AccessorTypes$Accessors;",
    "Lorg/jf/dexlib2/AccessorTypes;"
  ],
  "changedClasses": []
}
```

解读：以第一个文件为 OLD、第二个为 NEW，`addedClasses` 是 NEW 独有的类，`removedClasses` 是 OLD 独有的类，`changedClasses` 为空（两边没有同名类，自然没有「同名但方法体变化」）。退出码为 `1`（存在差异）。所有列表按字典序排序，输出**确定性**，适合作为回归基线 diff。

## 报告格式对照

文本格式（`--format text`，`DexDiff.toText()` 在 `DexDiff.java:225`）：

```
+ class Lcom/example/New;              # NEW 中新增的类
- class Lcom/example/Removed;          # OLD 中被删除的类
~ class Lcom/example/A;                # 两边都有、但方法有变化的类
    + Lcom/example/A;->added()V        # 新增方法
    - Lcom/example/A;->gone()V         # 删除方法
    ~ Lcom/example/A;->foo()I          # opcode 序列改动的方法
```

JSON 结构（`diff.toJson()`）：

```json
{
  "addedClasses":   ["Lcom/example/New;"],
  "removedClasses": ["Lcom/example/Removed;"],
  "changedClasses": [
    {
      "type": "Lcom/example/A;",
      "addedMethods":   ["Lcom/example/A;->added()V"],
      "removedMethods": ["Lcom/example/A;->gone()V"],
      "changedMethods": ["Lcom/example/A;->foo()I"]
    }
  ]
}
```

`ClassDiff` 内部结构定义在 `DexDiff.java:74`，三个列表字段 `addedMethods`/`removedMethods`/`changedMethods`。

## 退出码与脚本门控

| 退出码 | 含义 |
|--------|------|
| `0` | 两文件在 opcode 层面**语义一致** |
| `1` | 存在差异 |

```bash
# 仅当补丁只改了预期方法时才继续
baksmali diff orig.dex patched.dex && echo "未改动" || echo "有差异，请复核"
```

## 适用场景

| 场景 | 命令 | 关键看点 |
|------|------|---------|
| 验证补丁精确性 | `baksmali diff app.apk patched.dex` | 应只显示目标方法一处 `~`，确认 `patch`/`replace` 未误伤其他方法 |
| 版本升级审计 | `baksmali diff v1.apk v2.apk \| jq '.addedClasses'` | 两版间新增/删除了哪些类，默认 JSON 便于管道处理 |
| 恶意样本比对 | `baksmali diff original.apk repackaged.apk` | 判断「重打包」样本相对原版改动了哪些方法体 |
| 回归基线 | `baksmali diff base.dex cur.dex` + 退出码门控 | 确定性输出可入 CI，阻断语义回退 |
| 补丁链验证 | `baksmali patch ... -o p.dex && baksmali diff app.apk p.dex` | patch → diff 闭环，单方法改动一目了然 |

补丁精确性的典型用法：

```bash
baksmali patch app.apk --method isPremium --return true -o patched.dex
baksmali diff  app.apk patched.dex        # 应只显示 isPremium 一处 ~
```

## 与相关 skill 的关系

| Skill | 关系 |
|-------|------|
| [dex-read](./dex-read) | 编程读取的高级视图；diff 是「不编程、直接得语义差异」的对应物 |
| [dex-dump](./dex-dump) | 二进制字节级对比；diff 是语义级对比，两者粒度互补 |
| [dex-search](./dex-search) | 先定位目标方法，再 diff 确认其改动范围 |
| [dex-list-structure](./dex-list-structure) | 列类/方法清单，为 diff 提供「应有哪些类」的预期基线 |

需要更细的指令级 diff（含寄存器/引用），先各自 `baksmali disassemble` 再对反汇编文本做行级 diff；本命令专注「语义是否变化」这一层。

## 延伸阅读

- [CLI: baksmali diff](../cli/diff) — 命令行入口与全部选项
- [CLI: baksmali transform](../cli/transform) — `patch`/`replace` 命令，与 diff 构成补丁闭环
- [CLI: baksmali disassemble](../cli/disassemble) — 指令级行 diff 的前置工具
- [CLI: baksmali xref](../cli/xref) — 交叉引用，定位 diff 出的方法被谁调用
- 内幕: 语义 diff 模型 — `DexDiff` 纯模型与 opcode 归一化机制
- [SKILL.md 原文](https://github.com/android-security-engineer/smali-skills/blob/master/skills/dex-diff)
