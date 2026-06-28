---
name: dex-instructions
description: "Use when the user asks to: (1) check or identify instruction types in dex bytecode, (2) access instruction operands (registers, references, literals), (3) understand opcode formats and versions, (4) work with specific Dalvik instructions programmatically, (5) map between dex versions and API levels. Triggers: dexlib2 instruction, 指令类型, opcode, Opcode, Opcodes, instruction format, 指令格式, register, literal, reference, VersionMap, 版本映射, instruction interface."
---

# dex-instructions — dex 指令类型与 Opcode 版本

dexlib2 中 Dalvik 指令的类型体系、访问方式和 Opcode 版本映射。

## 前置条件

```bash
curl -fsSL -o dexlib2.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/dexlib2.jar
```

## 指令类型速查

### 按寄存器数量

```java
if (insn instanceof OneRegisterInstruction)       // vAA
    int reg = ((OneRegisterInstruction) insn).getRegister();

if (insn instanceof TwoRegisterInstruction)       // vA, vB
    int regA = ((TwoRegisterInstruction) insn).getRegisterA();
    int regB = ((TwoRegisterInstruction) insn).getRegisterB();

if (insn instanceof ThreeRegisterInstruction)     // vA, vB, vC
    int regA = ((ThreeRegisterInstruction) insn).getRegisterA();
    int regB = ((ThreeRegisterInstruction) insn).getRegisterB();
    int regC = ((ThreeRegisterInstruction) insn).getRegisterC();

if (insn instanceof FiveRegisterInstruction)      // vA, vB, vC, vD, vE (invoke-*)
    // 4 个参数寄存器 + 1 个寄存器计数
```

### 按引用类型

```java
if (insn instanceof ReferenceInstruction) {
    Reference ref = ((ReferenceInstruction) insn).getReference();
    // 具体类型: MethodReference, FieldReference, TypeReference, StringReference
}

if (insn instanceof MethodReferenceInstruction) {
    MethodReference ref = ((MethodReferenceInstruction) insn).getMethodReference();
}

if (insn instanceof FieldReferenceInstruction) {
    FieldReference ref = ((FieldReferenceInstruction) insn).getFieldReference();
}
```

### 按字面量类型

```java
if (insn instanceof NarrowLiteralInstruction)     // const/4, const/16, const
    int value = ((NarrowLiteralInstruction) insn).getNarrowLiteral();

if (insn instanceof WideLiteralInstruction)       // const-wide
    long value = ((WideLiteralInstruction) insn).getWideLiteral();

if (insn instanceof PayloadInstruction)           // fill-array-data, sparse-switch, packed-switch
    // 获取 payload 数据
```

### 按偏移类型

```java
if (insn instanceof OffsetInstruction)            // goto, if-*, invoke-*
    int offset = ((OffsetInstruction) insn).getCodeOffset();

if (insn instanceof SwitchPayload)                // sparse-switch, packed-switch
    // switch 表数据
```

## 常见指令格式

| 格式 | 语法 | 示例指令 |
|------|------|---------|
| `10x` | 无操作数 | `return-void` |
| `11x` | vAA | `move-result v0` |
| `12x` | vA, vB | `move v0, v1` |
| `21c` | vAA, ref@BBBB | `const-string v0, "hello"` |
| `22b` | vAA, vBB, #CC | `add-int/lit8 v0, v1, 1` |
| `22c` | vA, vB, ref@CCCC | `iget v0, v1, Lcom/Foo;->bar:I` |
| `35c` | {vC..vG}, ref@BBBB | `invoke-virtual {v0, v1}, Lcom/Foo;->bar(I)V` |
| `3rc` | {vCCCC..vNNNN}, ref@BBBB | `invoke-virtual/range {v0..v2}, ...` |

## Opcode 与版本支持

### 获取特定版本的 Opcode 集

```java
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.VersionMap;

// 按 API 级别
Opcodes opcodes = Opcodes.forApi(28);

// 按 dex 版本
Opcodes opcodes = Opcodes.forDexVersion(35);
```

### 检查 Opcode 可用性

```java
// 检查特定操作码是否在版本中可用
Opcode opcode = Opcode.INVOKE_CUSTOM;
boolean available = opcodes.isOpcodeSupported(opcode);
```

### 版本映射

```java
// dex 版本 → API 级别
int apiLevel = VersionMap.mapDexVersionToApi(35);   // → 15
int apiLevel = VersionMap.mapDexVersionToApi(37);   // → 21
int apiLevel = VersionMap.mapDexVersionToApi(38);   // → 26
int apiLevel = VersionMap.mapDexVersionToApi(39);   // → 28

// API 级别 → dex 版本
int dexVersion = VersionMap.mapApiToDexVersion(28);  // → 39
```

### 关键版本对应

| dex 版本 | API 级别 | 关键新增 |
|----------|---------|---------|
| 035 | 15 | 基础指令集 |
| 037 | 21 | art 相关 |
| 038 | 26 | invoke-custom, invoke-polymorphic |
| 039 | 28 | const-method-handle, const-method-type |

## 指令格式枚举

```java
import org.jf.dexlib2.Format;

// 获取指令格式
InstructionFormat format = insn.getOpcode().format;
// 值: Format10x, Format11x, Format12x, ..., Format3rc, Format51l
```

## 工具方法

```java
// 获取指令的代码单元大小
int codeUnits = insn.getCodeUnits();

// 获取指令的代码偏移（在遍历时需要自己跟踪）
// Instruction 本身不存储偏移，需在遍历时累加 codeUnits

// 判断指令类型
insn.getOpcode().format == Format.Format35c;  // invoke 系列
insn.getOpcode().format == Format.Format3rc;  // invoke/range 系列
```
