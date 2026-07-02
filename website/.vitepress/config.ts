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
        { text: 'GitHub', link: 'https://github.com/android-security-engineer/smali-skills' }
      ],

      sidebar: {
        '/guide/': [
          {
            text: '开始',
            items: [
              { text: '简介', link: '/guide/' },
              { text: '三层架构', link: '/guide/architecture' },
              { text: '安装', link: '/guide/install' },
              { text: '快速上手', link: '/guide/quickstart' }
            ]
          },
          {
            text: '工作流',
            items: [
              { text: '反汇编 ↔ 汇编往返', link: '/guide/roundtrip' },
              { text: '查询与交叉引用', link: '/guide/query' },
              { text: '写回变换', link: '/guide/transform' }
            ]
          }
        ],
        '/cli/': [
          {
            text: 'baksmali（反汇编/查询）',
            items: [
              { text: '概览', link: '/cli/' },
              { text: 'disassemble', link: '/cli/disassemble' },
              { text: 'list', link: '/cli/list' },
              { text: 'xref', link: '/cli/xref' },
              { text: 'search', link: '/cli/search' },
              { text: 'diff', link: '/cli/diff' },
              { text: 'fingerprint', link: '/cli/fingerprint' },
              { text: '变换命令', link: '/cli/transform' },
              { text: 'mcp', link: '/cli/mcp' }
            ]
          },
          {
            text: 'smali（汇编/工具）',
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
              { text: '总览', link: '/skills/' },
              { text: '读取/结构', link: '/skills/#读取-结构' },
              { text: '查询', link: '/skills/#查询' },
              { text: '比较/指纹', link: '/skills/#比较-指纹' },
              { text: '写回变换', link: '/skills/#写回变换' },
              { text: '编辑器/集成', link: '/skills/#编辑器-集成' }
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
