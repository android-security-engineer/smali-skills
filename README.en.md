# smali-skills

[![Website](https://img.shields.io/badge/website-VitePress-3c8d2c)](https://android-security-engineer.github.io/smali-skills/)　[![License](https://img.shields.io/badge/license-BSD--3--Clause-blue)](#relationship-to-upstream)　[![CI](https://github.com/android-security-engineer/smali-skills/actions/workflows/ci.yml/badge.svg)](https://github.com/android-security-engineer/smali-skills/actions/workflows/ci.yml)

**[简体中文](./README.md)** ｜ **[English](./README.en.md)**

📖 **Docs**：<https://android-security-engineer.github.io/smali-skills/>

---

smali/baksmali — an **AI-Agent-oriented** enhanced distribution of smali/baksmali. Built on top of
JesusFreke's original assembler/disassembler, it adds the **presentation and query layers**: JSON
output, cross-references, pattern search, statistical aggregation, and a full set of
progressively-disclosed Skills documentation.

> smali/baksmali is an assembler/disassembler for the dex format used by Dalvik (Android's VM). The
> syntax is loosely based on Jasmin/dedexer, with full support for all dex features (annotations,
> debug info, line numbers, etc.). smali text is a **lossless textual representation** of the dex
> binary — smali ⇄ dex round-trips 100%.

## Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 3 · Skills (progressively-disclosed Markdown, for AI) │
│  skills/*/SKILL.md  ——  27 fine-grained skills + index       │
├─────────────────────────────────────────────────────────────┤
│  Layer 2 · CLI (presentation/query layer — this repo's focus)│
│  baksmali:  disassemble / list / xref / search / diff /      │
│             fingerprint / transform / mcp (MCP server)       │
│  smali:     assemble / lsp (language server) / format / lint │
├─────────────────────────────────────────────────────────────┤
│  Layer 1 · dexlib2 (core library, the conversion engine)     │
│  iface / dexbacked / immutable / builder / writer / analysis │
└─────────────────────────────────────────────────────────────┘
```

- **Layer 1 (dexlib2)**: the core Java library for reading/writing/modifying dex — zero-copy parsing,
  mutable construction, pooled writing, deodex type inference. Version mapping extended to dex 040 / API 30+.
- **Layer 2 (CLI)**: the original only emitted plain text that agents had to regex-parse. This repo
  adds `--format json`, `xref`, `search`, `--count`/`--group-by` so agents can consume structured results.
- **Layer 3 (Skills)**: 27 SKILL.md files, organized in three progressive tiers (quick start / advanced /
  expert), covering every CLI capability and dexlib2 usage, loadable on demand by agents.

## Documentation Site

This repo hosts a full documentation site under [`website/`](./website/), built with **VitePress**
(guide / CLI / Skills / examples / code reference / internals, with mermaid diagrams and real
command→output examples): <https://android-security-engineer.github.io/smali-skills/>

```bash
cd website
npm install
npm run docs:dev      # local preview http://localhost:5173
npm run docs:build    # build to website/.vitepress/dist/
```

## Installation

```bash
git clone https://github.com/android-security-engineer/smali-skills.git
cd smali-skills
./gradlew build          # compile, test, build fat jars
```

Build artifacts (fat jars, with all dependencies):

- `smali/build/libs/smali.jar`     — assembler
- `baksmali/build/libs/baksmali.jar` — disassembler / query tool

Convenience wrapper scripts (thin `java -jar` shims):

```bash
scripts/smali    assemble ...        # equivalent to java -jar smali/build/libs/smali.jar assemble ...
scripts/baksmali disassemble ...     # equivalent to java -jar baksmali/build/libs/baksmali.jar disassemble ...
```

Requires Java 8+ (source target) and Java 11 (recommended for building; CI uses Java 11).

### Claude Code Plugin (marketplace)

This repo is also a **Claude Code plugin + marketplace** (`.claude-plugin/marketplace.json` +
`.claude-plugin/plugin.json`); the 27 skills are auto-discovered from `skills/*/SKILL.md`. In Claude Code:

```
/plugin marketplace add android-security-engineer/smali-skills
/plugin install smali-skills@smali-skills
```

After install, skills are invoked as `/smali-skills:<skill>`, e.g.:

```
/smali-skills:dex-xref
/smali-skills:dex-search
/smali-skills:dex-disassemble
```

The plugin provides only the Skills documentation layer; execution still calls the built
`baksmali.jar`/`smali.jar` (see installation above).

## What problem does this solve?

The upstream smali/baksmali is an excellent dex⇄smali converter, but has notable gaps for AI agents
and automated security analysis. smali-skills addresses them systematically:

| Capability | Upstream | smali-skills |
|------------|----------|--------------|
| Output format | plain text | JSON by default + `--format text` |
| Reverse cross-references | none | `xref` command |
| Instruction pattern search | none | `search --opcode` |
| One-line transforms | none | `transform unlock/replace/...` |
| Editor integration | none | LSP |
| Agent tool protocol | none | MCP |
| AI knowledge layer | none | 27 Skills |
| Fingerprint / clone detection | none | `fingerprint` |
| Semantic diff | none | `diff` |

See [the docs](https://android-security-engineer.github.io/smali-skills/guide/solved-problem) for details.

## Skills Index

27 skills live in `skills/`; the index is at [`skills/smali-skills/SKILL.md`](skills/smali-skills/SKILL.md),
grouped by capability:

- **Read/structure**: `dex-read`, `dex-list-structure`, `dex-list-classes`, `dex-list-methods`,
  `dex-list-strings`, `dex-multidex`
- **Query**: `dex-xref` (cross-references), `dex-search` (instruction pattern search)
- **Compare**: `dex-diff` (semantic diff of two dex/apk)
- **Fingerprint**: `dex-fingerprint` (opcode fingerprint, library/clone identification)
- **Transform**: `dex-transform` (unlock/replace/strip-debug/patch/callgraph)
- **Editor**: `smali-lsp` (LSP: diagnostics/outline/hover)
- **Format**: `smali-format` (format + lint, same style's fix side / check side)
- **Agent integration**: `smali-mcp` (MCP server: read-only dex queries as agent tools)
- **Convert**: `dex-disassemble`, `dex-assemble`, `dex-roundtrip`, `dex-build`
- **Analyze**: `dex-dump`, `dex-analyze`, `dex-instructions`, `dex-classpath`, `dex-deodex`
- **Rewrite**: `dex-rewrite-references`, `dex-rewrite-structure`
- **Basics**: `smali-syntax`, `smali-skills` (master index)

## Runnable Examples

`examples/` contains smali source examples (HelloWorld, Interface, Enums, InvokeCustom, etc.) that
run the full assemble → disassemble → list → xref loop end-to-end. See [`examples/scripts/`](examples/scripts/).

## Relationship to upstream

This repo is a fork of [JesusFreke/smali](https://github.com/JesusFreke/smali):

- The `upstream` remote tracks the original; `.github/workflows/sync-upstream.yml` auto-syncs upstream changes.
- All enhancements are **purely additive** (no changes to existing commands like `disassemble`/`assemble`);
  query commands (`list`/`xref`/`search`/`diff`/`fingerprint`) **default to JSON output** (for AI agents /
  scripts), with `--format text` to switch to human-readable text.
- CI (`.github/workflows/ci.yml`) builds and tests on Java 11 + Gradle 8.14.
- Release workflow (`.github/workflows/release.yml`) builds and publishes fat jars on tag.

## Build / Test

```bash
./gradlew build                                    # full: compile + all tests + fat jars
./gradlew :dexlib2:test                            # single module
./gradlew :baksmali:test --tests '*JsonOutputTest' # single test class
./gradlew :baksmali:fb                             # baksmali fast build (skip tests + javadoc)
```

The version is derived from the git HEAD short hash (e.g. `2.5.2-<hash>` or `-dirty`); release builds
drop the suffix.

## Resources

- [Official dex bytecode reference](https://source.android.com/devices/tech/dalvik/dalvik-bytecode.html)
- [Registers wiki](https://github.com/JesusFreke/smali/wiki/Registers)
- [Types/Methods/Fields wiki](https://github.com/JesusFreke/smali/wiki/TypesMethodsAndFields)
- [Official dex format reference](https://source.android.com/devices/tech/dalvik/dex-format.html)
- [Upstream JesusFreke/smali](https://github.com/JesusFreke/smali)
