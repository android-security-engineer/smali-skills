---
name: smali-lsp
description: "Use when the user asks to: (1) get smali diagnostics/error checking in an editor or IDE, (2) set up a smali Language Server / LSP, (3) get a document outline (classes/methods/fields) for a .smali file, (4) get hover documentation for smali opcodes/directives, (5) integrate smali editing into VS Code/Neovim/Emacs. Triggers: LSP, language server, smali lsp, diagnostics, 语言服务器, 诊断, document symbols, hover, 编辑器集成, editor integration, outline."
---

# smali-lsp — smali 语言服务器（LSP）

一个内置于 `smali.jar` 的 **Language Server**，通过 **stdio 上的 JSON-RPC**（标准
`Content-Length` 帧）与编辑器/IDE 通信。它把汇编器自身的词法/语法分析复用为实时能力，
无需第三方依赖（在项目已有的 Gson 之上手写协议，未引入 LSP4J / 网络依赖）。

## 提供的能力

| LSP 能力 | 效果 |
|----------|------|
| `publishDiagnostics` | 打开/修改文档时推送词法 + 语法 + 语义错误（含行列位置） |
| `textDocument/documentSymbol` | 类 → 方法/字段的层级大纲（方法名带 `(参数)返回类型` 签名） |
| `textDocument/hover` | 光标下 opcode/指令（`.method`、`invoke-virtual`…）的 Markdown 说明 |

`textDocumentSync` 为全量同步（每次改动发送整份文本）。

## 启动

```bash
# 通过 jar 直接启动（编辑器客户端应以此为 LSP 命令）
java -jar smali.jar lsp

# 或用包装脚本（自动定位同目录的 smali.jar）
scripts/smali-lsp
```

服务器从 stdin 读取、向 stdout 写入，不应交互式运行——由编辑器客户端拉起。

### 指定 API level

默认 API level 为 15。客户端可在 `initialize` 的 `initializationOptions` 中覆盖：

```json
{"initializationOptions": {"apiLevel": 34}}
```

## 编辑器接入示例

### Neovim（内置 LSP）

```lua
vim.api.nvim_create_autocmd('FileType', {
  pattern = 'smali',
  callback = function(args)
    vim.lsp.start({
      name = 'smali-lsp',
      cmd = { 'java', '-jar', '/path/to/smali.jar', 'lsp' },
      root_dir = vim.fs.dirname(args.file),
      init_options = { apiLevel = 34 },
    })
  end,
})
```

### VS Code

用 `vscode-languageclient` 写一个薄扩展，`serverOptions` 指向
`{ command: 'java', args: ['-jar', '/path/to/smali.jar', 'lsp'] }`，`documentSelector`
设为 `smali`。

## 协议速览（手动验证）

一次最小会话（每条消息都带 `Content-Length` 帧）：

```
→ initialize            ← { capabilities: { documentSymbolProvider, hoverProvider, textDocumentSync:1 } }
→ initialized (notif)
→ textDocument/didOpen   ← textDocument/publishDiagnostics（notif，含 diagnostics[]）
→ textDocument/documentSymbol ← [ { name:"Lcom/E;", kind:5, children:[…] } ]
→ textDocument/hover     ← { contents: { kind:"markdown", value:"**return-void** …" } }
→ shutdown               ← null
→ exit (notif)
```

诊断示例（缺少 `.super` 时）：

```json
{"uri":"file:///F.smali","diagnostics":[
  {"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":0}},
   "severity":1,"source":"smali","message":"The file must contain a .super directive"}]}
```

> 位置为 **0 基**（LSP 约定）：`line:0` 是第一行。底层 ANTLR 报的是 1 基行号，
> 服务器已在输出时减 1。

## 实现要点（源码位置）

- `smali/src/main/java/org/jf/smali/lsp/SmaliAnalyzer.java` — 纯分析核心：
  `diagnostics(source)` 收集 ERROR_CHANNEL 上的 `InvalidToken`（词法）+ 子类化
  `smaliParser` 捕获的 `RecognitionException`/`SemanticException`（语法/语义）；
  `documentSymbols(source)` 遍历 `I_CLASS_DEF`/`I_METHOD`/`I_FIELD` AST 节点。**无 I/O、
  无状态，可独立单测。**
- `smali/src/main/java/org/jf/smali/lsp/OpcodeDocs.java` — 精选 opcode/指令悬浮文档 +
  「族根回退」（`iget-object` 复用 `iget` 说明）+ dexlib2 `Opcode` 校验的通用回退。
- `smali/src/main/java/org/jf/smali/lsp/SmaliLanguageServer.java` — JSON-RPC 传输/派发层
  （`Content-Length` 帧读写、initialize/didOpen/didChange/hover/documentSymbol/shutdown/exit）。
- `smali/src/main/java/org/jf/smali/LspCommand.java` — `smali lsp` 子命令入口。

单元测试见 `smali/src/test/java/org/jf/smali/lsp/`（分析、opcode 文档、帧编解码 + 端到端
stdio 派发）。

## 相关 Skills

- `smali-syntax` — 手写 smali 时的语法参考（本 LSP 诊断的正是这套语法）
- `dex-disassemble` — 从 dex/apk 生成可供本 LSP 编辑的 `.smali` 文本
- `dex-assemble` — 把编辑后的 smali 重新汇编回 dex
