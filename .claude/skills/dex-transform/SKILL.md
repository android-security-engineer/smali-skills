---
name: dex-transform
description: "Use when the user asks to: (1) batch-modify a dex/apk and write out a NEW dex (not just inspect), (2) make classes/methods/fields public or non-final (publicize/definalize/unlock hidden APIs), (3) replace string constants across a dex, (4) strip debug info (line numbers/locals), (5) force a method to return a fixed value to bypass a check, (6) export a call graph. Triggers: unlock, publicize, definalize, 去final, 提权, 改访问标志, replace string, 替换字符串, strip debug, 去调试信息, patch method, force return, 绕过校验, bypass check, neuter method, callgraph, 调用图, baksmali unlock/replace/strip-debug/patch/callgraph."
---

# dex-transform — dex 写回变换命令

这是一组**修改并写出新 dex** 的 `baksmali` 子命令（区别于只读的 list/xref/search/disassemble）。它们把 dexlib2 的 `rewriter` 框架封装成一行即可运行的 CLI，无需写 Java 代码。

所有命令都：读入一个 dex/apk → 应用变换 → 用 `-o/--output`（默认 `out.dex`）写出一个新 dex。**原文件不被修改。**

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
alias baksmali='java -jar baksmali.jar'
```

## 命令速查

| 命令 | 作用 | 典型场景 |
|------|------|---------|
| `unlock` | 批量改访问标志：publicize / definalize | 打开隐藏 API、让类可被继承 |
| `replace` | 批量替换字符串常量 | 改 URL/日志 tag、脱敏 key |
| `strip-debug` | 清除全部调试信息 | 瘦身、增加逆向难度 |
| `patch` | 强制目标方法立即返回定值 | 绕过 root/授权/SSL 校验 |
| `callgraph` | 导出方法级调用图 | 静态分析、可视化 |

---

## unlock — 批量修改访问标志

把每个类/方法/字段变为 `public` 并/或去掉 `final`。

```bash
baksmali unlock app.apk -o unlocked.dex            # 两者都做（默认）
baksmali unlock app.apk --public   -o public.dex   # 仅 publicize（清 private/protected）
baksmali unlock app.apk --no-final -o open.dex     # 仅 definalize（去 final）
```

不带任何标志时，默认同时 publicize + definalize（“全部解锁”）。

## replace — 批量替换字符串常量

同时替换 `const-string`/`const-string/jumbo` 指令与字符串型 encoded value（如 `static final String` 初值）。

```bash
# 字面替换（--from 与 --to 按出现顺序配对）
baksmali replace app.apk --from http://old.example --to http://new.example -o patched.dex

# 多条规则，按顺序依次施加
baksmali replace app.apk --from DEBUG --to RELEASE --from v1 --to v2 -o patched.dex

# 正则替换，--to 可用 $1 引用捕获组
baksmali replace app.apk --regex "key_[0-9]+" --to REDACTED -o patched.dex
```

规则按命令行顺序依次作用于每个字符串（后一条看到前一条的输出）。

## strip-debug — 清除调试信息

移除每个方法的全部 debug item（行号 `.line`、局部变量 `.local`、参数名），保留可执行字节码不变。

```bash
baksmali strip-debug app.apk -o stripped.dex
```

## patch — 强制方法返回定值

把匹配 `--class`/`--method`（正则）的方法体整体替换为“立即返回”。返回值必须与方法返回类型兼容：

- `void` — 用于 `V` 返回
- `true`/`false`/`0`/`1` — 用于布尔/数值返回
- `null` — 用于对象/数组返回

```bash
# 让授权检查恒为 true
baksmali patch app.apk --method 'isPremium' --return true -o patched.dex

# 让某包下所有 check() 变成空操作
baksmali patch app.apk --class 'Lcom/drm/.*' --method 'check' --return void -o patched.dex
```

必须至少给出 `--class` 或 `--method` 之一，避免误伤全部方法。类型不兼容（如对 `I` 返回请求 `null`）会报错并中止。

## callgraph — 导出调用图

遍历每个方法体，把每次 invoke 记为一条 `调用者 -> 被调用者` 有向边。

```bash
baksmali callgraph app.apk                              # JSON（默认）
baksmali callgraph app.apk --graph-format dot > cg.dot  # Graphviz
baksmali callgraph app.apk --graph-format mermaid       # Mermaid 流程图
baksmali callgraph app.apk --class 'Lcom/example/.*'    # 只看某子系统
```

JSON 结构：`{"nodes":[...],"edges":[{"from":"...","to":"..."}]}`，节点用规范 smali 描述符 `Lpkg/Cls;->name(参数)返回类型`。

---

## 组合工作流示例

去混淆式“解锁 + 脱敏 + 瘦身”流水线：

```bash
baksmali unlock      app.apk        -o step1.dex
baksmali replace     step1.dex --from https://telemetry.example --to https://127.0.0.1 -o step2.dex
baksmali strip-debug step2.dex      -o final.dex
```

绕过校验后验证：

```bash
baksmali patch app.apk --class 'Lcom/app/License;' --method verify --return true -o patched.dex
baksmali disassemble patched.dex -o out/   # 查看反汇编确认已改为 return
```

## 底层机制

这些命令是 dexlib2 `rewriter` 框架（见 [dex-rewrite-structure](../dex-rewrite-structure/SKILL.md)、[dex-rewrite-references](../dex-rewrite-references/SKILL.md)）的成品封装：

- `unlock` → 覆写 `ClassDef/Field/Method` 的 `getAccessFlags()`
- `replace` → 自定义 `InstructionRewriter` + `EncodedValueRewriter` 改写字符串
- `strip-debug` → `MethodImplementationRewriter` 令 `getDebugItems()` 返回空
- `patch` → `MethodRewriter` 覆写 `getImplementation()` 为合成的返回体
- 写出统一走 `DexPool.writeTo(output, rewrittenDexFile)`

需要更细粒度控制（改类型/引用重映射/注入任意指令）时，直接用 dexlib2 库编程。
