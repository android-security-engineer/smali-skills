import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const smaliGrammar = JSON.parse(
  readFileSync(resolve(__dirname, 'smali-grammar.json'), 'utf-8')
)

export default withMermaid(
  defineConfig({
    lang: 'zh-CN',
    title: 'smali-skills',
    // GitHub Pages 项目站点子路径：https://android-security-engineer.github.io/smali-skills/
    base: '/smali-skills/',
    description: '面向 AI Agent 的 Android dex 字节码工具集 — 汇编/反汇编/分析/变换',
    lastUpdated: true,
    cleanUrls: true,

    head: [
      ['meta', { name: 'theme-color', content: '#3c8d2c' }],
      ['meta', { property: 'og:title', content: 'smali-skills' }],
      ['meta', {
        property: 'og:description',
        content: '面向 AI Agent 的 Android dex 字节码工具集 — 汇编/反汇编/分析/变换'
      }]
    ],

    markdown: {
      theme: {
        light: 'github-light',
        dark: 'github-dark'
      },
      lineNumbers: true,
      // 注册自定义 smali 语法（shiki 不内置），让 ```smali 代码块获得高亮
      shikiSetup: (shiki) => {
        shiki.loadLanguage({
          name: 'smali',
          scopeName: 'source.smali',
          grammar: smaliGrammar as any
        } as any)
      }
    },

    themeConfig: {
      siteTitle: 'smali-skills',
      logo: '/logo.svg',

      nav: [
        { text: '指南', link: '/guide/' },
        { text: 'CLI', link: '/cli/' },
        { text: 'Skills', link: '/skills/' },
        { text: '示例', link: '/examples/' },
        { text: '代码参考', link: '/reference/' },
        { text: '内部原理', link: '/internals/' },
        { text: 'GitHub', link: 'https://github.com/android-security-engineer/smali-skills' }
      ],

      sidebar: {
        '/guide/': [
          {
            text: '开始',
            items: [
              { text: '简介', link: '/guide/' },
              { text: '解决了什么问题', link: '/guide/solved-problem' },
              { text: '三层架构', link: '/guide/architecture' },
              { text: '安装', link: '/guide/install' },
              { text: '快速上手', link: '/guide/quickstart' },
              { text: '如何阅读源码', link: '/guide/reading-source' },
              { text: '常见问题', link: '/guide/faq' }
            ]
          },
          {
            text: '工作流',
            items: [
              { text: '反汇编 ↔ 汇编往返', link: '/guide/roundtrip' },
              { text: '查询与交叉引用', link: '/guide/query' },
              { text: '写回变换', link: '/guide/transform' },
              { text: '安全分析', link: '/guide/security-analysis' },
              { text: '逆向工程', link: '/guide/reverse-engineering' },
              { text: 'Agent 自动化集成', link: '/guide/agent-integration' }
            ]
          }
        ],
        '/cli/': [
          {
            text: '概览',
            items: [
              { text: 'CLI 概览', link: '/cli/' },
              { text: '📋 备忘单', link: '/cli/cheatsheet' }
            ]
          },
          {
            text: 'baksmali · 转换',
            items: [
              { text: 'disassemble', link: '/cli/disassemble' },
              { text: 'dump', link: '/reference/baksmali/commands/dump' }
            ]
          },
          {
            text: 'baksmali · 查询（默认 JSON）',
            items: [
              { text: 'list', link: '/cli/list' },
              { text: 'xref', link: '/cli/xref' },
              { text: 'search', link: '/cli/search' },
              { text: 'diff', link: '/cli/diff' },
              { text: 'fingerprint', link: '/cli/fingerprint' }
            ]
          },
          {
            text: 'baksmali · 变换与集成',
            items: [
              { text: 'transform', link: '/cli/transform' },
              { text: 'mcp', link: '/cli/mcp' },
              { text: 'deodex', link: '/reference/baksmali/commands/deodex' }
            ]
          },
          {
            text: 'smali · 汇编/工具',
            items: [
              { text: 'assemble', link: '/cli/assemble' },
              { text: 'format / lint', link: '/cli/format' },
              { text: 'lsp', link: '/cli/lsp' }
            ]
          }
        ],
        '/skills/': [
          {
            text: 'Skills 索引',
            items: [
              { text: '总览', link: '/skills/' }
            ]
          },
          {
            text: '读取/结构',
            items: [
              { text: 'dex-read', link: '/skills/dex-read' },
              { text: 'dex-list-structure', link: '/skills/dex-list-structure' },
              { text: 'dex-list-classes', link: '/skills/dex-list-classes' },
              { text: 'dex-list-methods', link: '/skills/dex-list-methods' },
              { text: 'dex-list-strings', link: '/skills/dex-list-strings' },
              { text: 'dex-multidex', link: '/skills/dex-multidex' }
            ]
          },
          {
            text: '查询',
            items: [
              { text: 'dex-xref', link: '/skills/dex-xref' },
              { text: 'dex-search', link: '/skills/dex-search' }
            ]
          },
          {
            text: '比较/指纹',
            items: [
              { text: 'dex-diff', link: '/skills/dex-diff' },
              { text: 'dex-fingerprint', link: '/skills/dex-fingerprint' }
            ]
          },
          {
            text: '写回变换',
            items: [
              { text: 'dex-transform', link: '/skills/dex-transform' },
              { text: 'dex-rewrite-references', link: '/skills/dex-rewrite-references' },
              { text: 'dex-rewrite-structure', link: '/skills/dex-rewrite-structure' }
            ]
          },
          {
            text: '转换',
            items: [
              { text: 'dex-disassemble', link: '/skills/dex-disassemble' },
              { text: 'dex-assemble', link: '/skills/dex-assemble' },
              { text: 'dex-roundtrip', link: '/skills/dex-roundtrip' },
              { text: 'dex-build', link: '/skills/dex-build' }
            ]
          },
          {
            text: '分析',
            items: [
              { text: 'dex-dump', link: '/skills/dex-dump' },
              { text: 'dex-analyze', link: '/skills/dex-analyze' },
              { text: 'dex-instructions', link: '/skills/dex-instructions' },
              { text: 'dex-classpath', link: '/skills/dex-classpath' },
              { text: 'dex-deodex', link: '/skills/dex-deodex' }
            ]
          },
          {
            text: '编辑器/集成',
            items: [
              { text: 'smali-lsp', link: '/skills/smali-lsp' },
              { text: 'smali-format', link: '/skills/smali-format' },
              { text: 'smali-mcp', link: '/skills/smali-mcp' }
            ]
          },
          {
            text: '基础',
            items: [
              { text: 'smali-syntax', link: '/skills/smali-syntax' },
              { text: 'smali-skills', link: '/skills/smali-skills' }
            ]
          }
        ],
        '/examples/': [
          {
            text: '示例',
            items: [
              { text: '总览', link: '/examples/' },
              { text: 'HelloWorld', link: '/examples/HelloWorld' },
              { text: 'AnnotationTypes', link: '/examples/AnnotationTypes' },
              { text: 'AnnotationValues', link: '/examples/AnnotationValues' },
              { text: 'BracketedMemberNames', link: '/examples/BracketedMemberNames' },
              { text: 'Enums', link: '/examples/Enums' },
              { text: 'Interface', link: '/examples/Interface' },
              { text: 'InvokeCustom', link: '/examples/InvokeCustom' },
              { text: 'MethodOverloading', link: '/examples/MethodOverloading' },
              { text: 'RecursiveAnnotation', link: '/examples/RecursiveAnnotation' },
              { text: 'RecursiveExceptionHandler', link: '/examples/RecursiveExceptionHandler' }
            ]
          }
        ],
        '/reference/': [
          {
            text: '代码参考',
            items: [
              { text: '总览', link: '/reference/' },
              { text: 'util 模块', link: '/reference/util' }
            ]
          },
          {
            text: 'dexlib2 · 子包',
            items: [
              { text: 'iface', link: '/reference/dexlib2/iface' },
              { text: 'iface/instruction', link: '/reference/dexlib2/iface-instruction' },
              { text: 'iface/formats', link: '/reference/dexlib2/iface-formats' },
              { text: 'iface/reference', link: '/reference/dexlib2/iface-reference' },
              { text: 'iface/value', link: '/reference/dexlib2/iface-value' },
              { text: 'iface/debug', link: '/reference/dexlib2/iface-debug' },
              { text: 'dexbacked', link: '/reference/dexlib2/dexbacked' },
              { text: 'dexbacked/raw', link: '/reference/dexlib2/dexbacked-raw' },
              { text: 'immutable', link: '/reference/dexlib2/immutable' },
              { text: 'builder', link: '/reference/dexlib2/builder' },
              { text: 'writer', link: '/reference/dexlib2/writer' },
              { text: 'writer/pool', link: '/reference/dexlib2/writer-pool' },
              { text: 'writer/builder', link: '/reference/dexlib2/writer-builder' },
              { text: 'rewriter', link: '/reference/dexlib2/rewriter' },
              { text: 'analysis', link: '/reference/dexlib2/analysis' },
              { text: 'formatter', link: '/reference/dexlib2/formatter' },
              { text: 'base', link: '/reference/dexlib2/base' },
              { text: 'util', link: '/reference/dexlib2/util' }
            ]
          },
          {
            text: 'dexlib2 · 核心类',
            items: [
              { text: 'DexFileFactory', link: '/reference/dexlib2/dexfile-factory' },
              { text: 'DexBackedDexFile', link: '/reference/dexlib2/dexbacked-dexfile' },
              { text: 'DexBackedClassDef', link: '/reference/dexlib2/dexbacked-classdef' },
              { text: 'ImmutableDexFile', link: '/reference/dexlib2/immutable-dexfile' },
              { text: 'ImmutableClassDef', link: '/reference/dexlib2/immutable-classdef' },
              { text: 'MutableMethodImplementation', link: '/reference/dexlib2/mutable-method-implementation' },
              { text: 'DexPool', link: '/reference/dexlib2/dex-pool' },
              { text: 'DexWriter', link: '/reference/dexlib2/dex-writer' },
              { text: 'ClassPath', link: '/reference/dexlib2/classpath' },
              { text: 'MethodAnalyzer', link: '/reference/dexlib2/method-analyzer' },
              { text: 'Opcode', link: '/reference/dexlib2/opcode' },
              { text: 'Opcodes', link: '/reference/dexlib2/opcodes' },
              { text: 'VersionMap', link: '/reference/dexlib2/version-map' },
              { text: 'DexRewriter', link: '/reference/dexlib2/dex-rewriter' },
              { text: 'RewriterModule', link: '/reference/dexlib2/rewriter-module' },
              { text: 'OatFile', link: '/reference/dexlib2/oat-file' },
              { text: 'ZipDexContainer', link: '/reference/dexlib2/zip-dex-container' },
              { text: 'InstructionFactory', link: '/reference/dexlib2/instruction-factory' }
            ]
          },
          {
            text: 'baksmali · 子包',
            items: [
              { text: 'main', link: '/reference/baksmali/main' },
              { text: 'Adaptors', link: '/reference/baksmali/adaptors' },
              { text: 'Adaptors/Debug', link: '/reference/baksmali/adaptors-debug' },
              { text: 'Adaptors/Format', link: '/reference/baksmali/adaptors-format' },
              { text: 'transform', link: '/reference/baksmali/transform' },
              { text: 'output', link: '/reference/baksmali/output' },
              { text: 'diff', link: '/reference/baksmali/diff' },
              { text: 'fingerprint', link: '/reference/baksmali/fingerprint' },
              { text: 'graph', link: '/reference/baksmali/graph' },
              { text: 'mcp', link: '/reference/baksmali/mcp' },
              { text: 'formatter', link: '/reference/baksmali/formatter' }
            ]
          },
          {
            text: 'baksmali · 命令',
            items: [
              { text: 'disassemble', link: '/reference/baksmali/commands/disassemble' },
              { text: 'dump', link: '/reference/baksmali/commands/dump' },
              { text: 'list', link: '/reference/baksmali/commands/list' },
              { text: 'list-classes', link: '/reference/baksmali/commands/list-classes' },
              { text: 'list-methods', link: '/reference/baksmali/commands/list-methods' },
              { text: 'list-strings', link: '/reference/baksmali/commands/list-strings' },
              { text: 'list-fields', link: '/reference/baksmali/commands/list-fields' },
              { text: 'list-types', link: '/reference/baksmali/commands/list-types' },
              { text: 'list-dex', link: '/reference/baksmali/commands/list-dex' },
              { text: 'list-vtables', link: '/reference/baksmali/commands/list-vtables' },
              { text: 'list-fieldoffsets', link: '/reference/baksmali/commands/list-fieldoffsets' },
              { text: 'list-dependencies', link: '/reference/baksmali/commands/list-dependencies' },
              { text: 'list-references', link: '/reference/baksmali/commands/list-references' },
              { text: 'xref', link: '/reference/baksmali/commands/xref' },
              { text: 'xref-callers', link: '/reference/baksmali/commands/xref-callers' },
              { text: 'xref-fieldrefs', link: '/reference/baksmali/commands/xref-fieldrefs' },
              { text: 'xref-typerefs', link: '/reference/baksmali/commands/xref-typerefs' },
              { text: 'xref-target', link: '/reference/baksmali/commands/xref-target' },
              { text: 'search', link: '/reference/baksmali/commands/search' },
              { text: 'diff', link: '/reference/baksmali/commands/diff' },
              { text: 'fingerprint', link: '/reference/baksmali/commands/fingerprint' },
              { text: 'transform', link: '/reference/baksmali/commands/transform' },
              { text: 'unlock', link: '/reference/baksmali/commands/unlock' },
              { text: 'replace', link: '/reference/baksmali/commands/replace' },
              { text: 'strip-debug', link: '/reference/baksmali/commands/strip-debug' },
              { text: 'patch', link: '/reference/baksmali/commands/patch' },
              { text: 'callgraph', link: '/reference/baksmali/commands/callgraph' },
              { text: 'deodex', link: '/reference/baksmali/commands/deodex' },
              { text: 'mcp', link: '/reference/baksmali/commands/mcp' }
            ]
          },
          {
            text: 'smali 模块',
            items: [
              { text: '总览', link: '/reference/smali/' },
              { text: 'AssembleCommand', link: '/reference/smali/assemble-command' },
              { text: '汇编管线', link: '/reference/smali/assembly-pipeline' },
              { text: 'SmaliLanguageServer', link: '/reference/smali/smali-language-server' },
              { text: 'SmaliFormatter', link: '/reference/smali/smali-formatter' },
              { text: 'SmaliLinter', link: '/reference/smali/smali-linter' },
              { text: 'SmaliOptions', link: '/reference/smali/smali-options' },
              { text: 'LiteralTools', link: '/reference/smali/literal-tools' },
              { text: 'util', link: '/reference/smali/util' }
            ]
          }
        ],
        '/internals/': [
          {
            text: '内部原理',
            items: [
              { text: '总览', link: '/internals/' },
              { text: 'DEX 文件格式', link: '/internals/dex-format' },
              { text: 'smali 语法参考', link: '/internals/smali-syntax' },
              { text: 'Opcode 参考', link: '/internals/opcodes' },
              { text: '版本映射', link: '/internals/version-map' }
            ]
          },
          {
            text: '核心机制',
            items: [
              { text: '零拷贝解析', link: '/internals/zero-copy' },
              { text: '池化写入', link: '/internals/pool-writing' },
              { text: '类型推断', link: '/internals/type-inference' },
              { text: 'deodex 机制', link: '/internals/deodex' },
              { text: '测试体系', link: '/internals/testing' }
            ]
          },
          {
            text: '集成与部署',
            items: [
              { text: 'Claude Code 插件', link: '/internals/plugin' },
              { text: 'MCP 集成', link: '/internals/mcp' },
              { text: 'GitHub Pages 部署', link: '/internals/deployment' }
            ]
          }
        ]
      },

      socialLinks: [
        { icon: 'github', link: 'https://github.com/android-security-engineer/smali-skills' }
      ],

      search: {
        provider: 'local',
        options: {
          translations: {
            button: { buttonText: '搜索', buttonAriaLabel: '搜索文档' },
            modal: {
              noResultsText: '无法找到相关结果',
              resetButtonTitle: '清除查询条件',
              footer: { selectText: '选择', navigateText: '切换' }
            }
          }
        }
      },

      footer: {
        message: '基于 BSD-3-Clause 协议发布',
        copyright: 'Copyright © 2026 Android Security Engineer'
      },

      outline: { level: [2, 3], label: '本页导航' },
      docFooter: { prev: '上一页', next: '下一页' },
      lastUpdatedText: '最后更新',
      returnToTopLabel: '回到顶部',
      sidebarMenuLabel: '菜单',
      darkModeSwitchLabel: '主题',
      lightModeSwitchTitle: '切换到浅色主题',
      darkModeSwitchTitle: '切换到深色主题'
    },

    mermaid: {
      // Mermaid 主题：随站点明暗切换
      theme: 'default'
    }
  })
)
