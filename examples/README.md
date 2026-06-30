# Examples

本目录提供 smali-skills 的端到端可运行示例。

## `scripts/e2e_demo.sh`

一个完整的闭环演示，串起所有 Layer-2 CLI 能力：

1. **assemble** — smali 文本 → dex
2. **disassemble** — dex → smali 文本
3. **list classes** + `--count`
4. **list methods** `--format json`
5. **list methods** `--group-by class`
6. **xref callers** — 反向方法调用查询
7. **xref field-refs** `--format json` — 反向字段引用查询
8. **search** `--opcode const-string,invoke-virtual` — opcode 序列匹配
9. **search** `--opcode const-string,*,return-void` — 通配符匹配

运行：

```bash
./gradlew build                      # 先构建 fat jar
bash examples/scripts/e2e_demo.sh    # 跑完整闭环
```

## smali 源码示例

`HelloWorld/`、`Interface/`、`Enums/`、`InvokeCustom/` 等子目录是独立的 smali 源码示例，
可单独 assemble：

```bash
java -jar smali/build/libs/smali.jar assemble examples/HelloWorld/HelloWorld.smali -o hello.dex
java -jar baksmali/build/libs/baksmali.jar disassemble hello.dex -o out/
```

每个示例展示 smali 语法的一个方面（注解、枚举、接口、invoke-custom、异常处理等）。
