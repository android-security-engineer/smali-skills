---
name: smali-syntax
description: "Use when the user asks to: (1) write or edit smali code, (2) understand smali file format and directives, (3) create a new smali class or method, (4) use Dalvik instructions in smali syntax, (5) fix smali assembly errors. Triggers: smali语法, smali syntax, write smali, 编写smali, smali directive, smali指令, .class, .method, .field, .registers, smali文件格式, how to write smali, smali example."
---

# smali-syntax — smali 语法参考

smali 文件格式、指令语法和常用模式的完整参考。

## 文件结构

每个 `.smali` 文件定义一个类，文件名与类名对应：

```
src/com/example/Main.smali  →  Lcom/example/Main;
```

### 最小类模板

```smali
.class public Lcom/example/Main;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method
```

## 类声明指令

```smali
.class <访问标志> <类名>          # 必须在文件第一行
.super <父类名>                   # 必须在 .class 之后
.implements <接口名>              # 可多个
.source <源文件名>                # 可选，调试用
```

### 访问标志

| 标志 | 值 | 说明 |
|------|-----|------|
| `public` | 0x01 | 公开 |
| `private` | 0x02 | 私有 |
| `protected` | 0x04 | 受保护 |
| `static` | 0x08 | 静态 |
| `final` | 0x10 | 不可继承/覆盖 |
| `abstract` | 0x400 | 抽象 |
| `interface` | 0x200 | 接口 |
| `enum` | 0x4000 | 枚举 |
| `synthetic` | 0x1000 | 编译器生成 |

## 字段声明

```smali
.field <访问标志> <字段名>:<类型>           # 实例字段
.field <访问标志> static <字段名>:<类型>    # 静态字段

# 带初始值的静态字段
.field public static final MAX_COUNT:I = 0x64

# 示例
.field private mName:Ljava/lang/String;
.field public static instance:Lcom/example/Singleton;
.field public static final PI:D = 3.141592653589793
```

## 方法声明

```smali
.method <访问标志> <方法名>(<参数类型>)<返回类型>
    .registers <N>       # 总寄存器数（或 .locals）
    <指令...>
.end method
```

### 构造函数

```smali
# 实例构造函数
.method public constructor <init>()V
    .registers 2
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

# 带参数的构造函数
.method public constructor <init>(Ljava/lang/String;)V
    .registers 3
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Lcom/example/Main;->mName:Ljava/lang/String;
    return-void
.end method
```

### 静态方法

```smali
.method public static main([Ljava/lang/String;)V
    .registers 2
    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;
    const-string v1, "Hello"
    invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V
    return-void
.end method
```

## 寄存器

```smali
.registers 5    # 总共 5 个寄存器 v0-v4
.locals 3       # 3 个局部寄存器 v0-v2，其余为参数寄存器

# 非 static 方法：p0 = this
# p1, p2, ... = 参数
# v0, v1, ... = 局部变量
```

### 寄存器映射

```
.registers 4, 方法签名 foo(IJ)V (非static)
  v0/p0 = this    (Ljava/lang/Object;)
  v1/p1 = arg1    (I)
  v2,v3/p2,p3 = arg2  (J, wide=2寄存器)
```

## 常用指令

### 移动

```smali
move v0, v1             # v0 = v1 (4位)
move-wide v0, v2        # 64位移动
move-object v0, v1      # 对象移动
move-result v0          # 取前一个 invoke 的返回值
move-result-object v0   # 取对象返回值
move-result-wide v0     # 取64位返回值
```

### 返回

```smali
return-void             # void 返回
return v0               # 返回 int/float
return-object v0        # 返回对象
return-wide v0          # 返回 long/double
```

### 常量

```smali
const/4 v0, 0x5         # 4位常量 (-8 ~ 7)
const/16 v0, 0x100      # 16位常量
const v0, 0x10000       # 32位常量
const-string v0, "text"  # 字符串常量
const-class v0, Lcom/Foo; # 类引用
```

### 实例操作

```smali
new-instance v0, Lcom/example/MyClass;
instance-of v0, v1, Lcom/example/MyClass;
check-cast v0, Lcom/example/MyClass;
```

### 字段访问

```smali
# 实例字段
iget v0, v1, Lcom/Foo;->bar:I
iput v0, v1, Lcom/Foo;->bar:I
iget-object v0, v1, Lcom/Foo;->bar:Ljava/lang/String;
iput-object v0, v1, Lcom/Foo;->bar:Ljava/lang/String;

# 静态字段
sget v0, Lcom/Foo;->bar:I
sput v0, Lcom/Foo;->bar:I
sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;
```

### 方法调用

```smali
invoke-virtual {v0, v1}, Lcom/Foo;->bar(I)V       # 虚方法
invoke-direct {v0}, Lcom/Foo;-><init>()V           # 构造/private
invoke-static {v0}, Lcom/Foo;->bar(I)V             # 静态方法
invoke-interface {v0, v1}, Lcom/Foo;->bar(I)V      # 接口方法
invoke-super {v0, v1}, Lcom/Foo;->bar(I)V          # 父类方法
```

### 算术

```smali
add-int v0, v1, v2        # v0 = v1 + v2
sub-int v0, v1, v2
mul-int v0, v1, v2
div-int v0, v1, v2
rem-int v0, v1, v2        # 取模

add-int/lit8 v0, v1, 0x1  # v0 = v1 + 1
```

### 比较

```smali
cmpl-float v0, v1, v2     # v0 = (v1 < v2) ? -1 : ((v1 == v2) ? 0 : 1)
cmpg-float v0, v1, v2
cmpl-double v0, v1, v2
cmp-long v0, v1, v2
```

### 条件跳转

```smali
if-eqz v0, :cond_0       # if (v0 == 0) goto :cond_0
if-nez v0, :cond_0       # if (v0 != 0)
if-eq v0, v1, :cond_0    # if (v0 == v1)
if-ne v0, v1, :cond_0
if-lt v0, v1, :cond_0    # if (v0 < v1)
if-ge v0, v1, :cond_0
if-gt v0, v1, :cond_0
if-le v0, v1, :cond_0
```

### 无条件跳转

```smali
goto :label_0             # 短跳转
goto/16 :label_0          # 中跳转
goto/32 :label_0          # 长跳转
```

### try/catch

```smali
.try-start
    invoke-virtual {v0}, Lcom/Foo;->bar()V
.try-end
.catch Ljava/lang/Exception; { :try-start .. :try-end } :catch-handler

.catch-handler
    move-exception v0
    invoke-virtual {v0}, Ljava/lang/Exception;->printStackTrace()V
```

## 类型描述符

| Smali | Java | Smali | Java |
|-------|------|-------|------|
| `V` | void | `J` | long |
| `Z` | boolean | `F` | float |
| `B` | byte | `D` | double |
| `C` | char | `Lcom/Foo;` | 对象 |
| `S` | short | `[I` | int[] |
| `I` | int | `[[B` | byte[][] |

## 完整示例

参见项目 `examples/` 目录中的 smali 文件：

| 示例 | 说明 |
|------|------|
| `HelloWorld/` | 最小可运行程序 |
| `AnnotationTypes/` | 类/方法/字段/参数注解 |
| `AnnotationValues/` | 各种注解值类型 |
| `Enums/` | 枚举类定义 |
| `Interface/` | 接口定义与实现 |
| `InvokeCustom/` | invoke-custom（Lambda） |
| `MethodOverloading/` | 方法重载 |
| `BracketedMemberNames/` | 括号成员名语法 |
