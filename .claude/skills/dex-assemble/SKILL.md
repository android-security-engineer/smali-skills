---
name: dex-assemble
description: "Use when the user asks to: (1) assemble smali text files into a dex binary, (2) compile smali code to dex format, (3) build a dex file from smali sources, (4) reassemble modified smali back to dex. Triggers: assemble, 汇编, smali to dex, compile smali, 构建dex, 重新汇编."
---

# dex-assemble — 将 smali 文本汇编为 dex 二进制

将 `.smali` 文本文件汇编为 Android Dalvik 可执行文件（`.dex`）。

## 前置条件

```bash
# 方式1: 一键安装（推荐）
curl -fsSL https://github.com/android-security-engineer/smali-skills/releases/latest/download/install.sh | bash

# 方式2: 仅下载 jar
curl -fsSL -o smali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/smali.jar

# 方式3: 从源码构建
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew :smali:build -x test -x javadoc
```

## 快速参考

```bash
# 基本汇编
java -jar smali.jar assemble -o <输出.dex> <输入目录|文件>

# 汇编整个目录（递归搜索 .smali 文件）
java -jar smali.jar a -o out.dex smali_src/

# 指定 API 级别（影响可用操作码）
java -jar smali.jar a -o out.dex -a 28 smali_src/
```

## 完整选项

```bash
java -jar smali.jar assemble \
  -o <输出.dex> \                    # 输出 dex 文件路径（默认 out.dex）
  -a <api级别> \                     # API 级别（默认 15），决定可用操作码
  -j <线程数> \                      # 并行编译线程数（默认 CPU 核心数）
  --allow-odex-opcodes \            # 允许汇编 odex 优化操作码
  --verbose \                       # 详细错误信息
  <文件或目录>                       # 可指定多个，目录会递归搜索 .smali 文件
```

## API 级别与操作码

不同 API 级别支持不同的 Dalvik 指令。常见对应：

| API 级别 | 关键新增 | 说明 |
|----------|----------|------|
| 15（默认） | 基础指令集 | 兼容最广 |
| 26 | invoke-custom, invoke-polymorphic | Lambda/MethodHandle 支持 |
| 28 | const-method-handle, const-method-type | 编译器内联优化 |

若汇编时遇到 "invalid instruction" 错误，尝试提高 API 级别。

## 典型工作流

### 从零创建 dex

```bash
# 1. 编写 smali 文件
mkdir -p src/com/example
cat > src/com/example/HelloWorld.smali << 'EOF'
.class public Lcom/example/HelloWorld;
.super Ljava/lang/Object;

.method public static main([Ljava/lang/String;)V
    .registers 2
    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;
    const-string v1, "Hello, World!"
    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V
    return-void
.end method
.end class
EOF

# 2. 汇编
java -jar smali.jar a -o hello.dex src/

# 3. 验证
java -jar baksmali.jar d -o verified hello.dex
diff -r src/ verified/
```

### 修改-重汇编 round-trip

```bash
# 1. 反汇编原始 APK
java -jar baksmali.jar d -o smali_out original.apk

# 2. 修改 smali 文件
vim smali_out/com/example/Main.smali

# 3. 重新汇编
java -jar smali.jar a -o modified.dex smali_out/

# 4. 替换回 APK
cp modified.dex extracted/classes.dex
# 重新打包并签名...
```

## 参考示例

项目 `examples/` 目录包含完整的 smali 示例：

| 示例 | 说明 |
|------|------|
| `HelloWorld/` | 最小可运行程序 |
| `AnnotationTypes/` | 类/方法/字段/参数注解 |
| `AnnotationValues/` | 各种注解值类型 |
| `Enums/` | 枚举类定义 |
| `Interface/` | 接口定义与实现 |
| `InvokeCustom/` | invoke-custom（Lambda/MethodHandle） |
| `MethodOverloading/` | 方法重载 |
| `BracketedMemberNames/` | 括号成员名语法 |

```bash
# 汇编示例
java -jar smali.jar a -o helloworld.dex examples/HelloWorld/
```

## 常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| `invalid instruction` | API 级别太低，不支持该指令 | 加 `-a` 提高到对应 API 级别 |
| `odex opcode not allowed` | 使用了 odex 优化操作码 | 加 `--allow-odex-opcodes` |
| `Duplicate class` | 同一个类出现在多个 .smali 文件中 | 检查输入文件去重 |
| `No .source or .class directive` | smali 文件格式错误 | 检查文件头部 |
