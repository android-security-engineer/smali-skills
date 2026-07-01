---
name: smali-mcp
description: "Use when the user wants an AI agent host (Claude Desktop, IDE agents, other MCP clients) to inspect dex/apk files through the Model Context Protocol instead of shelling out and parsing text. Triggers: MCP, Model Context Protocol, mcp server, baksmali mcp, agent tools, tools/list, tools/call, 让 agent 直接查 dex, 把 baksmali 接入 Claude, MCP 工具, stdio JSON-RPC, expose dex to LLM."
---

# smali-mcp — 把 baksmali 只读查询暴露为 MCP 工具

`baksmali mcp` 启动一个 **Model Context Protocol** 服务器（stdio 上的 JSON-RPC 2.0），把 baksmali 的只读 dex 查询能力包装成 MCP **tools**，让支持 MCP 的 AI Agent 宿主（Claude Desktop、IDE agent 等）**直接调用**，而不必自己 shell 出去再正则解析文本。

- 传输：**逐行 JSON**（一行一个 JSON-RPC 消息）over stdin/stdout —— MCP 在 stdio 上最常用的帧格式。
- 无第三方 MCP/JSON-RPC 依赖，协议手写在项目已有的 Gson 上（与 `smali lsp` 同一思路）。
- 待检查的 dex/apk **不在命令行给**，而是每次 `tools/call` 时用 `input` 参数传路径——一个常驻进程可服务任意多个文件。

## 前置条件

```bash
curl -fsSL -o baksmali.jar https://github.com/android-security-engineer/smali-skills/releases/latest/download/baksmali.jar
```

## 启动

```bash
java -jar baksmali.jar mcp            # 由 MCP 宿主拉起（不用于交互式手敲）
java -jar baksmali.jar mcp -a 30      # 指定 API level（影响反汇编输出/opcode 集）
```

### 接入 Claude Desktop（示例）

在 `claude_desktop_config.json` 的 `mcpServers` 中加入：

```json
{
  "mcpServers": {
    "baksmali": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/baksmali.jar", "mcp"]
    }
  }
}
```

重启宿主后，agent 即可看到并调用下列工具。

## 暴露的工具（全部只读）

| 工具 | 参数 | 返回 |
|------|------|------|
| `list_dex` | `input`（必填）、`type`=classes\|methods\|strings\|fields\|types（默认 classes） | JSON 数组 |
| `disassemble_class` | `input`、`class`（如 `Lcom/example/Foo;`） | 该类的 smali 文本 |
| `search_opcodes` | `input`、`opcode`（如 `const-string,invoke-virtual`，`*` 匹配任意单条） | 命中方法列表 |
| `xref` | `input`、`target`、`kind`=callers\|field-refs\|type-refs（默认 callers） | 反向引用点列表 |

工具执行错误（找不到类、参数缺失等）以 `isError: true` 的正常结果返回（MCP 约定），不是协议级错误——agent 能读到人类可读的错误文本并自我纠正。

## 协议速览

```
→ initialize                 ← {protocolVersion, capabilities:{tools}, serverInfo}
→ notifications/initialized  （通知，无响应）
→ tools/list                 ← {tools:[{name, description, inputSchema}]}
→ tools/call {name, arguments} ← {content:[{type:"text", text}], isError}
→ ping                       ← {}
```

每个工具都带 JSON-Schema `inputSchema`，宿主据此校验/提示参数。

## 手动冒烟测试（不接宿主）

直接把 JSON-RPC 行喂给 stdin 即可验证：

```bash
{
  echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
  echo '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_dex","arguments":{"input":"app.apk","type":"methods"}}}'
} | java -jar baksmali.jar mcp
```

## 典型场景

**让 agent 侦察一个陌生 APK**：agent 先 `list_dex(type=classes)` 看结构，再 `search_opcodes` 定位可疑指令序列，`xref` 追调用链，最后 `disassemble_class` 精读——全程结构化 JSON，不需要 agent 解析人类文本。

**多文件会话**：一个 `mcp` 进程可反复被 `tools/call` 指向不同 `input`，做跨样本对比（配合 `dex-diff`/`dex-fingerprint` 的 CLI 结果一起用）。

## 底层机制

- 纯协议层 `org.jf.baksmali.mcp.McpServer#handle(JsonObject)` 无副作用、可单测；dex 加载走一个可注入的 `Function<String,DexFile>`（默认 `DexFileFactory`），测试用内存 fixture 即可端到端验证工具。
- 工具实现复用现有能力：`list_dex` 复用 `output/JsonOutput` 的 schema，`search_opcodes` 复用 `PatternSearcher`，`xref` 复用 `ReferenceFinder`，`disassemble_class` 复用 `Adaptors/ClassDefinition` + `BaksmaliWriter`——与对应 CLI 子命令（`list`/`search`/`xref`/`disassemble`）输出一致。

相关：需要在编辑器里写 smali 用 `smali-lsp`；命令行做同样查询用 `dex-list-*`/`dex-search`/`dex-xref`/`dex-disassemble`。
