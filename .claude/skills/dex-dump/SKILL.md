---
name: dex-dump
description: "Use when the user asks to: (1) dump a hex view of a dex file with annotations, (2) inspect raw dex binary structure, (3) understand dex section layout, (4) debug dex format issues. Triggers: dump, hex dump, 十六进制转储, annotated dump, raw dex, dex结构, 二进制查看."
---

# dex-dump — dex 文件带注释的十六进制转储

输出 dex 文件的带注释十六进制转储，标注各 section 的含义和边界。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 快速参考

```bash
# 基本转储
java -jar baksmali/build/libs/baksmali.jar dump <dex文件>

# 转储到文件
java -jar baksmali/build/libs/baksmali.jar dump app.dex > dump.txt
```

## 输出说明

转储输出会标注 dex 文件各 section：

- **Header** — 魔数、校验和、文件大小、各 section 偏移
- **String IDs** — 字符串表偏移和索引
- **Type IDs** — 类型描述符表
- **Proto IDs** — 方法原型表
- **Field IDs** — 字段引用表
- **Method IDs** — 方法引用表
- **Class Defs** — 类定义表
- **Data** — 各 section 实际数据
- **Map List** — section 映射

每个 section 的字节都有偏移量、十六进制值和语义注释。

## 适用场景

| 场景 | 说明 |
|------|------|
| 理解 dex 格式 | 学习 dex 二进制结构 |
| 调试 dex 写入 | 检查自己生成的 dex 是否正确 |
| 验证 section 对齐 | 检查各 section 偏移是否符合规范 |
| 分析损坏的 dex | 定位二进制层面的问题 |

## 示例

```bash
# 转储并查看 header
java -jar baksmali/build/libs/baksmali.jar dump classes.dex | head -40

# 搜索特定偏移
java -jar baksmali/build/libs/baksmali.jar dump classes.dex | grep "0x0008"

# 保存完整转储
java -jar baksmali/build/libs/baksmali.jar dump classes.dex > classes.dump.txt
```
