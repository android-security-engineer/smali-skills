---
name: dex-dump
description: "Use when the user asks to: (1) dump a hex view of a dex file with annotations, (2) inspect raw dex binary structure, (3) understand dex section layout, (4) debug dex format issues, (5) verify dex header fields or checksums. Triggers: dump, hex dump, 十六进制转储, annotated dump, raw dex, dex结构, 二进制查看, dex header, dex校验, section偏移, map list."
---

# dex-dump — dex 文件带注释的十六进制转储

输出 dex 文件的带注释十六进制转储，标注各 section 的含义和边界。用于理解 dex 二进制格式和调试格式问题。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 快速参考

```bash
# 基本转储
java -jar baksmali.jar dump <dex文件>

# 转储到文件
java -jar baksmali.jar dump app.dex > dump.txt

# 指定 API 级别
java -jar baksmali.jar dump -a 28 app.dex

# 转储 APK 中的特定 dex
java -jar baksmali.jar dump "app.apk/classes2.dex"
```

## 完整选项

```bash
java -jar baksmali.jar dump \
  -a <api级别> \        # API 级别（默认 -1 = 自动检测）
  <dex/apk/odex/oat文件>
```

## dex 文件格式概览

```
┌──────────────────────────────┐ 0x0000
│ Header (0x70 bytes)          │ 魔数、校验和、文件大小、各 section 偏移
├──────────────────────────────┤ 0x0070
│ String IDs                   │ 字符串偏移表
├──────────────────────────────┤
│ Type IDs                     │ 类型描述符索引表
├──────────────────────────────┤
│ Proto IDs                    │ 方法原型表
├──────────────────────────────┤
│ Field IDs                    │ 字段引用表
├──────────────────────────────┤
│ Method IDs                   │ 方法引用表
├──────────────────────────────┤
│ Class Defs                   │ 类定义表
├──────────────────────────────┤
│ Data                         │ 各 section 实际数据
├──────────────────────────────┤
│ Map List                     │ section 映射（用于遍历）
└──────────────────────────────┘
```

## 输出格式详解

### Header 区域

```
000000: 64 65 78 0a 30 33 35 00  |dex.035.|  magic: dex\n035\0
000008: 3a 21 4b c3              |:!K.    |  checksum (Adler32)
00000c: 1e 6b 48 5f 9c 3e 0b 5a  |.kH_.>.Z|  signature (SHA-1)
000020: 00 04 01 00              |....    |  file_size: 0x00010400
000024: 70 00 00 00              |p...    |  header_size: 0x70
000028: 00 00 00 00              |....    |  endian_tag: 0x12345678
...
```

每个 section 输出包含：
- **偏移量**：十六进制地址
- **十六进制值**：原始字节
- **ASCII**：可打印字符
- **语义注释**：字段含义

### Section 详解

| Section | 内容 | dump 中的注释 |
|---------|------|--------------|
| **Header** | 文件头 | 魔数、版本、校验和、签名、各 section 偏移和大小 |
| **String IDs** | 字符串偏移表 | 每个条目指向 Data 区的字符串 |
| **Type IDs** | 类型索引 | 每个条目是 String IDs 的索引 |
| **Proto IDs** | 方法原型 | 返回类型 + 参数类型列表 |
| **Field IDs** | 字段引用 | 类名 + 类型 + 名称索引 |
| **Method IDs** | 方法引用 | 类名 + 原型 + 名称索引 |
| **Class Defs** | 类定义 | 类信息、父类、接口、字段/方法偏移 |
| **Data** | 实际数据 | 字符串内容、指令字节码、注解等 |
| **Map List** | section 映射 | 类型→偏移→大小的映射表 |

## 适用场景

### 理解 dex 格式

```bash
# 查看 header 中的关键字段
java -jar baksmali.jar dump classes.dex | head -40

# 查看特定 section
java -jar baksmali.jar dump classes.dex | grep -A5 "String IDs"
```

### 调试 dex 写入

```bash
# 检查自己生成的 dex 是否正确
java -jar baksmali.jar dump output.dex > my_dump.txt

# 对比两个 dex 的差异
diff <(java -jar baksmali.jar dump original.dex) <(java -jar baksmali.jar dump rebuilt.dex)
```

### 验证 section 对齐

```bash
# 检查各 section 偏移是否符合规范
java -jar baksmali.jar dump classes.dex | grep "offset\|size"
```

### 分析损坏的 dex

```bash
# 定位二进制层面的问题
java -jar baksmali.jar dump corrupted.dex 2>&1 | head -50

# 搜索特定偏移位置的内容
java -jar baksmali.jar dump classes.dex | grep "0x0008"
```

### 检查校验和

```bash
# 查看 header 中的 checksum 和 signature
java -jar baksmali.jar dump classes.dex | head -8
```

## 实用技巧

```bash
# 保存完整转储供后续分析
java -jar baksmali.jar dump classes.dex > classes.dump.txt

# 只看 header（前 0x70 字节对应的注释区域）
java -jar baksmali.jar dump classes.dex | head -40

# 搜索字符串数据区
java -jar baksmali.jar dump classes.dex | grep -A2 "string_data"

# 搜索类定义区
java -jar baksmali.jar dump classes.dex | grep -A5 "class_def"

# 导出为文件，配合 hexdumper 使用
java -jar baksmali.jar dump large.dex > large.dump.txt
wc -l large.dump.txt
```
