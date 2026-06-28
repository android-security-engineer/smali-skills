---
name: dex-list-classes
description: "Use when the user asks to: (1) list classes in a dex/apk file, (2) enumerate class names or type descriptors, (3) find what classes an APK contains, (4) list fields or types in a dex file. Triggers: list classes, 类列表, class list, list types, 类型表, list fields, 字段表, find class, 查找类, baksmali list c, baksmali list t, baksmali list f."
---

# dex-list-classes — 列举 dex 文件中的类、类型和字段

快速浏览 dex/apk 文件中的类定义、类型描述符和字段引用。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 列举类

```bash
# 列举所有类
java -jar baksmali.jar list classes app.apk

# 短别名
java -jar baksmali.jar l c app.apk
```

输出每行一个类描述符：

```
Lcom/example/Main;
Lcom/example/Network$1;    # 匿名内部类
Lcom/example/Network$Callback;  # 命名内部类
Landroid/app/Activity;
```

### 搜索类

```bash
# 搜索特定包的类
java -jar baksmali.jar l c app.apk | grep "com/example"

# 搜索 Activity
java -jar baksmali.jar l c app.apk | grep "Activity"

# 搜索内部类
java -jar baksmali.jar l c app.apk | grep '\$'

# 排除 Android 框架类，只看应用类
java -jar baksmali.jar l c app.apk | grep -v "^Landroid/\|^Landroidx/\|^Lkotlin/\|^Lkotlinx/"
```

## 列举类型

```bash
# 列举所有类型 ID
java -jar baksmali.jar list types app.apk

# 短别名
java -jar baksmali.jar l t app.apk
```

输出所有类型描述符，包括基本类型：

```
I                        # int
Z                        # boolean
Ljava/lang/String;       # String
Lcom/example/Main;       # 自定义类
[B                       # byte[]
[[I                      # int[][]
```

### 类型描述符速查

| 描述符 | Java 类型 | 描述符 | Java 类型 |
|--------|----------|--------|----------|
| `V` | void | `J` | long |
| `Z` | boolean | `F` | float |
| `B` | byte | `D` | double |
| `C` | char | `L...;` | 对象类型 |
| `S` | short | `[` | 数组（前置） |
| `I` | int | | |

## 列举字段

```bash
# 列举所有字段
java -jar baksmali.jar list fields app.apk

# 短别名
java -jar baksmali.jar l f app.apk
```

输出格式：`类名->字段名:类型`

```
Lcom/example/Main;->mContext:Landroid/content/Context;
Lcom/example/Config;->API_KEY:Ljava/lang/String;
Lcom/example/User;->id:I
```

### 搜索字段

```bash
# 搜索特定字段名
java -jar baksmali.jar l f app.apk | grep "API_KEY\|SECRET\|TOKEN"

# 搜索特定类型的字段
java -jar baksmali.jar l f app.apk | grep ":Landroid/widget/EditText;"

# 搜索静态字段
java -jar baksmali.jar l f app.apk | grep "com/example.*->.*:Ljava/lang/String;"
```

## 多 dex APK

```bash
# 列举特定 dex 的类
java -jar baksmali.jar l c "app.apk/classes2.dex"

# 列举特定 dex 的字段
java -jar baksmali.jar l f "app.apk/classes2.dex"
```

## 典型场景

| 场景 | 命令 |
|------|------|
| 查看应用包结构 | `java -jar baksmali.jar l c app.apk \| grep "^Lcom/myapp"` |
| 查找敏感字段 | `java -jar baksmali.jar l f app.apk \| grep -iE "key\|secret\|token\|password"` |
| 统计类数量 | `java -jar baksmali.jar l c app.apk \| wc -l` |
| 查找自定义 View | `java -jar baksmali.jar l c app.apk \| grep "View\|Layout"` |
| 查找接口实现 | `java -jar baksmali.jar l c app.apk \| grep "Impl\|Listener"` |
