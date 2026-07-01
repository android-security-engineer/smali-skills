---
name: dex-fingerprint
description: "Use when the user asks to: (1) fingerprint methods/classes/dex by opcode (rename-invariant hashing), (2) identify a known library inside an app even after obfuscation/renaming, (3) detect clones or repackaged/copied code, (4) match classes in one dex against a reference dex by similarity, (5) dedup structurally-identical methods. Triggers: fingerprint, 指纹, 特征, library identification, 库识别, detect library, clone detection, 克隆检测, 查重, repackage, 重打包, opcode n-gram, similarity, 相似度, rename-invariant, baksmali fingerprint."
---

# dex-fingerprint — opcode 指纹与库/克隆识别

`baksmali fingerprint` 基于**方法体的 opcode 序列**（不含任何名字/引用）计算指纹，因此**对重命名不敏感**：混淆器把类/方法/字段改名、换寄存器号，只要字节码逻辑不变，指纹就相同。这正是用它识别“被改名的已知库”“被重打包/抄袭的代码”的原理。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
alias baksmali='java -jar baksmali.jar'
```

## 两种模式

### 1. 列举指纹（默认）

对每个 方法 / 类 / 整个 dex 输出一个短哈希（16 位十六进制，取 SHA-256 前 64 位）。

```bash
baksmali fingerprint app.apk                      # 每个类一行：<hash>  <类型>
baksmali fingerprint app.apk --level method       # 细到方法
baksmali fingerprint app.apk --level dex          # 整个 dex 一个哈希
baksmali fingerprint app.apk --class 'Lokhttp3/.*'  # 只看某包
# 默认就是 JSON（机器可读）；要人读文本加 --format text
```

- **方法哈希** = 该方法 opcode 序列的哈希。
- **类哈希** = 其所有方法哈希**排序后**再哈希（与方法声明顺序、类名、成员名都无关）。
- **dex 哈希** = 其所有类哈希排序后再哈希。

两个仅仅改了名字的类，类哈希**完全相同** —— 可直接用来查重/去重。

### 2. 匹配参考库（`--match`）

把输入的每个类，用 **opcode n-gram 的加权 Jaccard 相似度**去和一个参考 dex 里的类逐一比较，报告相似度 ≥ 阈值的最佳匹配。指向一个已知库的 dex，就能在 app 里“认出”这个库——即使被混淆过。

```bash
# 在 app 里找 okhttp（指向已知版本的 okhttp classes.dex）
baksmali fingerprint app.apk --match okhttp.dex --min-similarity 0.9

baksmali fingerprint app.apk --match lib.dex --ngram 4      # 用 4-gram，更严格
# 默认 JSON：[{class, match, similarity}]；要人读文本加 --format text
```

- `--ngram N`：n-gram 窗口大小，默认 `3`。越大越严格、越不容忍改动。
- `--min-similarity F`：报告阈值，默认 `0.85`（`1.0` = 完全一致）。

**为什么用 n-gram 而不是哈希？** 精确哈希只能判“完全相同”；一旦库被插桩、加了一两条指令，哈希就变了。opcode n-gram 的 Jaccard 相似度对小改动**鲁棒**，因此适合识别“被轻微修改过的库”。哈希用于精确去重，n-gram 用于模糊识别，两者互补。

## 典型场景

**识别第三方库**（即使被混淆）：

```bash
baksmali fingerprint suspicious.apk --match known-sdk.dex --min-similarity 0.8
```

**查抄袭/重打包**——同一份代码被改名后重新发布：

```bash
# 两个 app 的类哈希取交集，即为“结构相同（可能仅改名）”的类
baksmali fingerprint appA.apk | jq -r '.[].fingerprint' | sort > a.txt
baksmali fingerprint appB.apk | jq -r '.[].fingerprint' | sort > b.txt
comm -12 a.txt b.txt   # 共有指纹 = 疑似复用代码
```

**方法去重**——统计一个 dex 里有多少组“字节码完全相同”的方法：

```bash
baksmali fingerprint app.apk --level method \
  | jq -r '.[].fingerprint' | sort | uniq -d
```

## 真实示例

用仓库自带的 `accessorTest.dex` fixture（含 `AccessorTypes` 及其内部类 `AccessorTypes$Accessors`）：

```bash
# 默认 JSON：每个类一行指纹
java -jar baksmali.jar fingerprint dexlib2/src/test/resources/accessorTest.dex
```

实际输出：

```json
[
  {"class":"Lorg/jf/dexlib2/AccessorTypes$Accessors;","fingerprint":"63594b5c1fa69ee4"},
  {"class":"Lorg/jf/dexlib2/AccessorTypes;","fingerprint":"206203f971d55342"}
]
```

人读文本对照（`--format text`）：

```
63594b5c1fa69ee4  Lorg/jf/dexlib2/AccessorTypes$Accessors;
206203f971d55342  Lorg/jf/dexlib2/AccessorTypes;
```

`63594b5c1fa69ee4` 这种 16 位十六进制短哈希就是「类内所有方法 opcode 序列排序后再哈希」的结果。把两份 dex 的指纹集合取交集（见下「查抄袭」），即可在**不含任何名字**的前提下找出结构相同的类。

## 底层机制

- 纯模型 `org.jf.baksmali.fingerprint.Fingerprint`（无 I/O）：`methodHash`/`classHash`/`dexHash`、`ngrams`、`classNgramProfile`、`jaccard`。
- opcode 序列取自 `MethodImplementation.getInstructions()` 的 `Opcode.name`；abstract/native 方法为空序列。
- 加权 Jaccard = `Σ min(计数) / Σ max(计数)`，两侧空袋定义为 `1.0`。

相关：只想知道两个 dex 之间“哪些方法体变了”，用 `dex-diff`（精确 opcode diff）；想按 opcode 序列搜代码用 `dex-search`。
