---
name: dex-diff
description: "Use when the user asks to: (1) compare two dex/apk files and see what changed, (2) find which classes/methods were added/removed/modified between two versions of an app, (3) diff two APK builds semantically (ignoring recompilation noise), (4) verify whether a patched dex actually changed only the intended methods. Triggers: diff, 差异, 对比, compare dex, compare apk, 比较两个, what changed, added/removed classes, changed methods, 版本对比, baksmali diff, semantic diff, 语义差异."
---

# dex-diff — 两个 dex/apk 的语义差异

`baksmali diff OLD NEW` 在**操作码（opcode）层面**比较两个 dex/apk，报告：

- **新增/删除的类**（类型描述符层面）
- **对同时存在的类**：新增 / 删除 / **改动**的方法

方法被判为“改动”的依据是**opcode 序列不同**。寄存器分配、调试信息（`.line`/`.local`/参数名）、指令偏移都被**刻意忽略**——所以“同源重编译产生的噪声”不会被误报为语义改动。这正是它区别于 `diff <(baksmali d a) <(baksmali d b)` 纯文本对比的地方。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
alias baksmali='java -jar baksmali.jar'
```

## 用法

```bash
# 默认就是 JSON（机器可读）；两个位置参数：OLD NEW
baksmali diff old.apk new.apk
# 要人读文本：显式 --format text
baksmali diff old.apk new.apk --format text
```

两个位置参数按顺序为 OLD、NEW。支持和其它命令一致的 `apk/oat` 多 dex 条目语法（如 `app.apk/classes2.dex`）。

### 退出码（可用于脚本门控）

- `0` —— 两文件在 opcode 层面**语义一致**
- `1` —— 存在差异

```bash
# 仅当补丁只改了预期方法时才继续
baksmali diff orig.dex patched.dex && echo "未改动" || echo "有差异，请复核"
```

## 文本报告格式

```
+ class Lcom/example/New;              # NEW 中新增的类
- class Lcom/example/Removed;          # OLD 中被删除的类
~ class Lcom/example/A;                # 两边都有、但方法有变化的类
    + Lcom/example/A;->added()V        # 新增方法
    - Lcom/example/A;->gone()V         # 删除方法
    ~ Lcom/example/A;->foo()I          # opcode 序列改动的方法
```

## JSON 结构

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

所有列表都按字典序排序，输出**确定性**，适合作为回归基线 diff。

## 真实示例

用仓库自带的两个不同 fixture dex 对比——`accessorTest.dex`（含 `AccessorTypes` 两个类）
与 `LocalTest/classes.dex`（含 `LLocalTest;` 一个类），二者类集合完全不重叠：

```bash
java -jar baksmali.jar diff \
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

解读：以第一个文件为 OLD、第二个为 NEW，`addedClasses` 是 NEW 独有的类，`removedClasses`
是 OLD 独有的类，`changedClasses` 为空（因为两边没有同名的类，自然没有「同名但方法体变化」）。
退出码为 `1`（存在差异）。

## 典型场景

**验证补丁精确性**——确认 `patch`/`replace` 只改了目标方法：

```bash
baksmali patch app.apk --method isPremium --return true -o patched.dex
baksmali diff  app.apk patched.dex        # 应只显示 isPremium 一处 ~
```

**版本升级审计**——两个 APK 版本间新增/删除了哪些类：

```bash
baksmali diff v1.apk v2.apk | jq '.addedClasses'   # 默认就是 JSON
```

**恶意样本比对**——判断“重打包”样本相对原版改动了哪些方法体。

## 底层机制

- 类以类型描述符为键，方法以规范描述符 `Lcls;->name(参数)返回类型` 为键。
- 每个方法体归一化为**逗号连接的 opcode 名序列**；abstract/native（无方法体）归一化为空串。一边有体、另一边无体也算改动。
- 纯模型 `org.jf.baksmali.diff.DexDiff`（无 I/O），`DiffCommand` 负责加载与打印。

需要更细的指令级 diff（含寄存器/引用），先各自 `baksmali disassemble` 再对反汇编文本做行级 diff；本命令专注“语义是否变化”这一层。相关：方法指纹/库识别见 `dex-fingerprint`（如已提供）。
