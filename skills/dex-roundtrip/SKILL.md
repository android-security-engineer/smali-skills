---
name: dex-roundtrip
description: "Use when the user asks to: (1) disassemble, modify, and reassemble a dex/APK file, (2) perform a round-trip on dex files, (3) modify Android bytecode and rebuild, (4) patch an APK at the smali level, (5) verify round-trip integrity. Triggers: round-trip, 往返, disassemble and reassemble, 反汇编重汇编, modify dex, 修改dex, patch APK, 打包APK, repackage, 重打包, edit smali, 修改smali."
---

# dex-roundtrip — 反汇编→修改→重汇编完整工作流

从 dex/APK 反汇编为 smali 文本，修改后重新汇编为 dex 的完整流程。

## 前置条件

```bash
curl -fsSL -o smali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/smali.jar
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 基本流程

```
dex/apk ──baksmali disassemble──→ smali文本 ──编辑──→ 修改后smali ──smali assemble──→ 新dex
```

## 步骤 1：反汇编

```bash
# 反汇编 APK（自动识别 zip 中的 classes.dex）
java -jar baksmali.jar d -o smali_out app.apk

# 反汇编 dex 文件
java -jar baksmali.jar d -o smali_out classes.dex

# 只反汇编特定类
java -jar baksmali.jar d -o smali_out --classes Lcom/example/Main app.apk

# 省略调试信息（更干净的输出，方便编辑）
java -jar baksmali.jar d -o smali_out --debug-info=false app.apk

# 使用顺序标签（方便引用和编辑）
java -jar baksmali.jar d -o smali_out --sequential-labels app.apk
```

## 步骤 2：编辑 smali

```bash
# 找到目标文件
find smali_out -name "Main.smali"

# 编辑
vim smali_out/com/example/Main.smali
```

### 常见修改模式

#### 修改字符串常量

```smali
# 修改前
const-string v1, "old_url"

# 修改后
const-string v1, "new_url"
```

#### 跳过方法调用

```smali
# 修改前
invoke-virtual {v0, v1}, Lcom/example/Check;->verify()Z

# 修改后（直接返回 true）
const/4 v0, 0x1
```

#### 注入日志

```smali
# 在方法开头注入
const-string v0, "TAG"
const-string v1, "method entered"
invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
```

#### 修改方法返回值

```smali
# 修改前
.method public isPremium()Z
    .registers 2
    # ... 复杂逻辑 ...
    return v0
.end method

# 修改后（直接返回 true）
.method public isPremium()Z
    .registers 1
    const/4 v0, 0x1
    return v0
.end method
```

## 步骤 3：重新汇编

```bash
# 汇编为 dex
java -jar smali.jar a -o modified.dex smali_out/

# 指定 API 级别（如果使用了高版本指令）
java -jar smali.jar a -o modified.dex -a 28 smali_out/
```

## 步骤 4：替换回 APK

```bash
# 1. 解压 APK
mkdir apk_contents
unzip app.apk -d apk_contents

# 2. 替换 classes.dex
cp modified.dex apk_contents/classes.dex

# 3. 删除旧签名
rm -rf apk_contents/META-INF

# 4. 重新打包
cd apk_contents
zip -r ../modified.apk .
cd ..

# 5. 对齐
zipalign -f 4 modified.apk aligned.apk

# 6. 签名
apksigner sign --ks my-key.jks --out signed.apk aligned.apk
```

## 处理多 dex APK

```bash
# 1. 查看包含哪些 dex
java -jar baksmali.jar l d app.apk

# 2. 反汇编特定 dex
java -jar baksmali.jar d -o smali_out2 "app.apk/classes2.dex"

# 3. 编辑后重汇编
java -jar smali.jar a -o classes2.dex smali_out2/

# 4. 替换回 APK
cp classes2.dex apk_contents/classes2.dex
```

## 验证 round-trip 完整性

```bash
# 方法1：重新反汇编并对比
java -jar baksmali.jar d -o verified modified.dex
diff -r smali_out/ verified/

# 方法2：使用 md5 校验
md5sum original.dex
# 汇编后再反汇编
java -jar smali.jar a -o rebuilt.dex smali_out/
java -jar baksmali.jar d -o roundtrip rebuilt.dex
diff -r smali_out/ roundtrip/
```

### 真实往返示例（assemble ⇄ disassemble）

用 `examples/HelloWorld/` 实跑完整闭环。源 smali：

```smali
.class public LHelloWorld;
.super Ljava/lang/Object;

.method public static main([Ljava/lang/String;)V
    .registers 2

    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;

    const-string	v1, "Hello World!"

    invoke-virtual {v0, v1}, Ljava/lang/PrintStream;->println(Ljava/lang/String;)V

    return-void
.end method
```

```bash
# 1) 汇编 smali → dex
java -jar smali.jar assemble -o /tmp/hello.dex examples/HelloWorld/

# 2) 反汇编 dex → smali（往返）
java -jar baksmali.jar disassemble -o /tmp/hello_rt /tmp/hello.dex
```

往返后 `/tmp/hello_rt/HelloWorld.smali` 的指令序列与源**完全一致**：

```smali
.class public LHelloWorld;
.super Ljava/lang/Object;


# direct methods
.method public static main([Ljava/lang/String;)V
    .registers 2

    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;

    const-string p0, "Hello World!"

    invoke-virtual {v0, p0}, Ljava/lang/PrintStream;->println(Ljava/lang/String;)V

    return-void
.end method
```

唯一的差异是寄存器命名规范化：源里写的 `v1`，反汇编输出为 `p0`（参数寄存器命名约定）——
这是**同义**写法，对应同一个物理寄存器，字节码层面完全等价。用 `baksmali dump` 比对两次
产物的指令字节即可确认零差异。

## 处理 odex 文件

odex 文件需要先 deodex 才能重汇编：

```bash
# 1. 去 odex
java -jar baksmali.jar deodex -o smali_out \
  --boot-class-path /system/framework/framework.jar \
  app.odex

# 2. 编辑 smali_out/...

# 3. 重新汇编（deodex 后的 smali 可以正常汇编）
java -jar smali.jar a -o modified.dex smali_out/
```

## 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| 汇编报 `invalid instruction` | API 级别太低 | 加 `-a` 提高到对应级别 |
| 汇编报 `odex opcode not allowed` | 使用了 odex 指令 | 先 deodex 或加 `--allow-odex-opcodes` |
| 汇编报 `Duplicate class` | 同一个类在多个 .smali 中 | 检查输入文件去重 |
| 重打包后闪退 | 签名问题 | 确保重新签名 |
| 多 dex 类找不到 | 修改的类在另一个 dex 中 | 用 `l d` 确认并指定正确的 dex |
