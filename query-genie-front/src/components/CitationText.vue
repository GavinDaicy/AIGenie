<template>
  <span class="citation-text" v-html="rendered" @click.stop="handleClick"></span>
</template>

<script>
/**
 * 引用文本渲染组件：将答案文本中的 [N] 标记渲染为可点击角标。
 *
 * Props:
 *   text      - 原始答案文本（含 [N] 标记）
 *   citations - CitationItem[] 引用数据列表
 *
 * Events:
 *   cite-click(citation) - 点击角标时触发，携带对应的 CitationItem
 */
export default {
  name: 'CitationText',
  props: {
    text: { type: String, default: '' },
    citations: { type: Array, default: () => [] }
  },
  computed: {
    rendered() {
      if (!this.text) return ''
      let html = this.text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')

      const citationMap = {}
      ;(this.citations || []).forEach(c => { citationMap[c.index] = c })

      html = html.replace(/\[(\d+)\]/g, (match, num) => {
        const idx = parseInt(num, 10)
        if (!citationMap[idx]) return match
        return `<sup data-cite="${idx}"><span class="ct-badge">[${idx}]</span></sup>`
      })

      html = html.replace(/\n/g, '<br>')
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
