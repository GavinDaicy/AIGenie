<template>
  <div class="citation-text markdown-body" v-html="rendered" @click.stop="handleClick"></div>
</template>

<script>
import { marked } from 'marked'

/**
 * 引用文本渲染组件：将答案文本中的 [N] 标记渲染为可点击角标，并支持 Markdown 格式。
 *
 * Props:
 *   text      - 原始答案文本（含 [N] 标记，支持 Markdown 语法）
 *   citations - CitationItem[] 引用数据列表
 *
 * Events:
 *   cite-click(citation) - 点击角标时触发，携带对应的 CitationItem
 */

const renderer = new marked.Renderer()

export default {
  name: 'CitationText',
  props: {
    text: { type: String, default: '' },
    citations: { type: Array, default: () => [] }
  },
  computed: {
    rendered() {
      if (!this.text) return ''

      const citationMap = {}
      ;(this.citations || []).forEach(c => { citationMap[c.index] = c })

      let src = this.text.replace(/\[(\d+)\]/g, (match, num) => {
        const idx = parseInt(num, 10)
        if (!citationMap[idx]) return match
        return `\x00CITE${idx}\x00`
      })

      let html = marked.parse(src, { renderer, breaks: true, gfm: true })

      html = html.replace(/\x00CITE(\d+)\x00/g, (match, num) => {
        const idx = parseInt(num, 10)
        return `<sup data-cite="${idx}"><span class="ct-badge">[${idx}]</span></sup>`
      })

      return html
    }
  },
  methods: {
    handleClick(e) {
      const badge = e.target.closest('[data-cite]')
      if (!badge) return
      const idx = parseInt(badge.getAttribute('data-cite'), 10)
      const citation = (this.citations || []).find(c => c.index === idx)
      if (citation) this.$emit('cite-click', citation)
    }
  }
}
</script>

<style scoped>
.citation-text {
  line-height: 1.8;
  word-break: break-word;
}

.citation-text :deep(h1),
.citation-text :deep(h2),
.citation-text :deep(h3),
.citation-text :deep(h4),
.citation-text :deep(h5),
.citation-text :deep(h6) {
  margin: 0.6em 0 0.3em;
  font-weight: 600;
  line-height: 1.4;
}
.citation-text :deep(h1) { font-size: 1.4em; }
.citation-text :deep(h2) { font-size: 1.25em; }
.citation-text :deep(h3) { font-size: 1.1em; }

.citation-text :deep(p) {
  margin: 0.4em 0;
}

.citation-text :deep(ul),
.citation-text :deep(ol) {
  margin: 0.3em 0;
  padding-left: 1.5em;
}
.citation-text :deep(li) {
  margin: 0.2em 0;
}

.citation-text :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  border-radius: 3px;
  padding: 0.1em 0.4em;
  font-size: 0.88em;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.citation-text :deep(pre) {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 6px;
  padding: 0.8em 1em;
  overflow-x: auto;
  margin: 0.5em 0;
}
.citation-text :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 0.85em;
}

.citation-text :deep(blockquote) {
  border-left: 3px solid #ddd;
  margin: 0.4em 0;
  padding: 0.2em 0.8em;
  color: #666;
}

.citation-text :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
  font-size: 0.9em;
}
.citation-text :deep(th),
.citation-text :deep(td) {
  border: 1px solid #ddd;
  padding: 6px 12px;
  text-align: left;
}
.citation-text :deep(th) {
  background: rgba(0, 0, 0, 0.04);
  font-weight: 600;
}

.citation-text :deep(a) {
  color: var(--qg-primary, #409eff);
  text-decoration: none;
}
.citation-text :deep(a):hover {
  text-decoration: underline;
}

.citation-text :deep(hr) {
  border: none;
  border-top: 1px solid #eee;
  margin: 0.8em 0;
}

.citation-text :deep(strong) { font-weight: 700; }
.citation-text :deep(em) { font-style: italic; }

.citation-text :deep(.ct-badge) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  padding: 0 3px;
  height: 16px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--qg-primary, #409eff) 12%, transparent);
  color: var(--qg-primary, #409eff);
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s ease;
  user-select: none;
  vertical-align: middle;
}
.citation-text :deep(.ct-badge):hover {
  background: color-mix(in srgb, var(--qg-primary, #409eff) 22%, transparent);
}
</style>
