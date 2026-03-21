<template>
  <div class="search-page qg-page">
    <div class="search-top">
      <div class="page-hero">
        <h1 class="page-hero-title">检索</h1>
        <p class="page-hero-desc">关键词、语义与混合模式；条件在上方，结果在下方滚动浏览</p>
      </div>
      <el-card class="filters-card" shadow="never">
        <el-form :model="form" class="query-form inline-form">
          <div class="filter-grid">
            <el-form-item class="query-item compact-item">
              <el-input
                v-model="form.query"
                size="small"
                placeholder="输入关键词或自然语言问句，按 Enter 直接检索"
                clearable
                @keydown.enter.native.prevent="handleSearch"
              >
                <i
                  slot="suffix"
                  class="el-input__icon el-icon-search query-search-icon"
                  @click="handleSearch"
                />
              </el-input>
            </el-form-item>
            <el-form-item class="compact-item">
              <el-select
                v-model="form.knowledgeCodes"
                multiple
                collapse-tags
                size="small"
                placeholder="不选则检索全部已发布知识库"
                style="width: 100%"
              >
                <el-option
                  v-for="kl in knowledgeList"
                  :key="kl.code"
                  :label="kl.name"
                  :value="kl.code"
                />
              </el-select>
            </el-form-item>
            <el-form-item class="compact-item">
              <el-radio-group v-model="form.mode" size="small">
                <el-radio-button label="KEYWORD">关键字</el-radio-button>
                <el-radio-button label="VECTOR">语义</el-radio-button>
                <el-radio-button label="HYBRID">混合</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item class="compact-item action-item">
              <div class="filter-actions">
                <el-button size="small" @click="resetForm">重置</el-button>
                <el-button size="small" plain icon="el-icon-setting" @click="showAdvancedDrawer = true">高级筛选</el-button>
              </div>
            </el-form-item>
          </div>
        </el-form>
      </el-card>
    </div>
    <div class="results-panel">
      <div class="results-head">
        <h2 class="results-title">结果</h2>
        <div class="result-head-actions">
          <span v-if="results.length" class="result-count">共 {{ results.length }} 条</span>
          <el-switch
            v-model="compactMode"
            size="mini"
            active-text="紧凑展示"
            inactive-text="完整展示"
          />
        </div>
      </div>
      <el-skeleton v-if="loading" animated :rows="8" />
      <template v-else-if="results.length">
        <el-card v-for="(item, idx) in results" :key="idx" class="result-item" shadow="never">
          <div class="result-meta">
            <div class="meta-left">
              <el-tag size="small">{{ item.knowledgeCode }}</el-tag>
              <span v-if="item.document" class="doc-name">{{ item.document.name }}</span>
            </div>
            <div class="meta-right">
              <span class="score">相关度 {{ (item.score || 0).toFixed(4) }}</span>
              <el-button type="text" class="raw-btn" @click="openRawDialog(item)">查看原始</el-button>
            </div>
          </div>
          <div class="result-content">
            <template v-if="item.chunkContent">
              <template v-for="(val, key) in getVisibleChunkEntries(item, idx)">
                <div v-if="val != null && val !== ''" :key="key" class="chunk-field">
                  <span class="field-key">{{ key }}：</span>
                  <span class="field-val" v-html="buildReadableSnippet(formatChunkValue(val))" />
                </div>
              </template>
              <div v-if="compactMode && getHiddenFieldCount(item, idx) > 0" class="expand-row">
                <el-button type="text" @click="toggleExpandedResult(idx)">
                  {{ isResultExpanded(idx) ? '收起字段' : `展开其余 ${getHiddenFieldCount(item, idx)} 个字段` }}
                </el-button>
              </div>
            </template>
            <span v-else class="no-content">无分块内容</span>
          </div>
        </el-card>
      </template>
      <el-empty v-else-if="searched" description="暂无检索结果，请修改条件后重试" />
      <el-empty v-else description="请输入检索条件开始检索" />
    </div>
    <el-drawer
      title="高级筛选"
      :visible.sync="showAdvancedDrawer"
      size="360px"
      direction="rtl"
    >
      <div class="advanced-body">
        <el-form :model="form" label-position="top" size="small">
          <el-form-item label="返回条数">
            <el-input-number v-model="form.size" :min="1" :max="50" style="width: 100%" />
          </el-form-item>
          <el-form-item label="得分归一化">
            <el-switch
              v-model="form.normalizeScore"
              active-text="0~10"
              inactive-text="原始分"
            />
          </el-form-item>
          <el-form-item label="Rerank 重排序">
            <el-switch
              v-model="form.rerank"
              active-text="开启"
              inactive-text="关闭"
            />
          </el-form-item>
          <el-form-item label="时间衰减">
            <el-switch
              v-model="form.useTimeDecay"
              active-text="开启"
              inactive-text="关闭"
            />
          </el-form-item>
        </el-form>
      </div>
    </el-drawer>
    <el-dialog
      title="原始完整信息"
      :visible.sync="showRawDialog"
      width="760px"
    >
      <div v-if="activeRawItem" class="raw-dialog-body">
        <div class="raw-meta">
          <el-tag size="small">{{ activeRawItem.knowledgeCode || '-' }}</el-tag>
          <span class="raw-score">相关度 {{ ((activeRawItem.score || 0)).toFixed(4) }}</span>
          <span v-if="activeRawItem.document && activeRawItem.document.name" class="raw-doc">
            {{ activeRawItem.document.name }}
          </span>
        </div>
        <el-tabs>
          <el-tab-pane label="完整字段">
            <div v-if="activeRawItem.chunkContent && Object.keys(activeRawItem.chunkContent).length" class="raw-fields">
              <div
                v-for="(val, key) in activeRawItem.chunkContent"
                :key="key"
                class="raw-field-row"
              >
                <div class="raw-key">{{ key }}</div>
                <div class="raw-val">{{ formatChunkValue(val) }}</div>
              </div>
            </div>
            <el-empty v-else description="无分块内容" />
          </el-tab-pane>
          <el-tab-pane label="原始 JSON">
            <pre class="raw-json">{{ formatJson(activeRawItem) }}</pre>
          </el-tab-pane>
        </el-tabs>
      </div>
      <div v-else>未找到原始数据</div>
    </el-dialog>
  </div>
</template>

<script>
import { getKnowledgeList } from '@/api/knowledge'
import { search } from '@/api/query'

export default {
  name: 'Search',
  data() {
    return {
      knowledgeList: [],
      form: {
        query: '',
        knowledgeCodes: [],
        mode: 'HYBRID',
        size: 10,
        normalizeScore: true,
        rerank: false,
        useTimeDecay: false
      },
      showAdvancedDrawer: false,
      compactMode: true,
      expandedResultKeys: {},
      showRawDialog: false,
      activeRawItem: null,
      loading: false,
      searched: false,
      results: []
    }
  },
  created() {
    this.loadKnowledgeList()
  },
  methods: {
    async loadKnowledgeList() {
      try {
        const list = await getKnowledgeList()
        this.knowledgeList = (list || []).filter(kl => kl.status === 2 || kl.status === 1)
      } catch (e) {
        this.knowledgeList = []
      }
    },
    async handleSearch() {
      if (!this.form.query.trim()) {
        this.$message.warning('请输入检索内容')
        return
      }
      this.loading = true
      this.searched = true
      try {
        const payload = {
          query: this.form.query.trim(),
          mode: this.form.mode,
          size: this.form.size,
          normalizeScore: this.form.normalizeScore,
          rerank: this.form.rerank,
          useTimeDecay: this.form.useTimeDecay
        }
        if (this.form.knowledgeCodes.length) payload.knowledgeCodes = this.form.knowledgeCodes
        this.results = await search(payload) || []
      } catch (e) {
        this.results = []
      } finally {
        this.loading = false
      }
    },
    resetForm() {
      this.form = { query: '', knowledgeCodes: [], mode: 'HYBRID', size: 10, normalizeScore: true, rerank: false, useTimeDecay: false }
      this.results = []
      this.expandedResultKeys = {}
      this.searched = false
    },
    formatChunkValue(val) {
      if (typeof val === 'string') return val
      if (typeof val === 'object' && val !== null) return JSON.stringify(val)
      return String(val)
    },
    escapeHtml(raw) {
      return String(raw)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
    },
    escapeRegExp(raw) {
      return String(raw).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    },
    isResultExpanded(idx) {
      return !!this.expandedResultKeys[idx]
    },
    toggleExpandedResult(idx) {
      this.$set(this.expandedResultKeys, idx, !this.isResultExpanded(idx))
    },
    getFilteredChunkEntries(item) {
      if (!item || !item.chunkContent || typeof item.chunkContent !== 'object') return []
      return Object.entries(item.chunkContent).filter(([, val]) => val != null && val !== '')
    },
    getVisibleChunkEntries(item, idx) {
      const entries = this.getFilteredChunkEntries(item)
      if (!this.compactMode || this.isResultExpanded(idx)) return entries
      return entries.slice(0, 3)
    },
    getHiddenFieldCount(item, idx) {
      const entries = this.getFilteredChunkEntries(item)
      if (!this.compactMode || this.isResultExpanded(idx)) return 0
      return Math.max(entries.length - 3, 0)
    },
    getQueryTokens() {
      const tokens = (this.form.query || '')
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 12)
      return Array.from(new Set(tokens))
    },
    highlightWithGroups(raw) {
      const source = String(raw == null ? '' : raw)
      const tokens = this.getQueryTokens()
      if (!tokens.length) return this.escapeHtml(source)
      const metas = tokens
        .map((token, originalIndex) => ({ token, originalIndex }))
        .sort((a, b) => b.token.length - a.token.length)
      const pattern = metas.map(meta => `(${this.escapeRegExp(meta.token)})`).join('|')
      if (!pattern) return this.escapeHtml(source)
      const regex = new RegExp(pattern, 'gi')
      const segments = []
      let lastIndex = 0
      let match
      while ((match = regex.exec(source)) !== null) {
        const start = match.index
        const end = start + match[0].length
        if (start > lastIndex) {
          segments.push(this.escapeHtml(source.slice(lastIndex, start)))
        }
        let matchedMetaIdx = 0
        for (let i = 1; i < match.length; i += 1) {
          if (match[i] !== undefined) {
            matchedMetaIdx = i - 1
            break
          }
        }
        const groupClass = `hl-group-${metas[matchedMetaIdx].originalIndex % 6}`
        segments.push(`<mark class="${groupClass}">${this.escapeHtml(source.slice(start, end))}</mark>`)
        lastIndex = end
        if (regex.lastIndex === match.index) regex.lastIndex += 1
      }
      if (lastIndex < source.length) {
        segments.push(this.escapeHtml(source.slice(lastIndex)))
      }
      return segments.join('')
    },
    buildReadableSnippet(raw) {
      const text = String(raw || '')
      const tokens = this.getQueryTokens()
      if (!tokens.length) {
        const fallback = text.length > 240 ? `${text.slice(0, 240)}...` : text
        return this.escapeHtml(fallback)
      }
      const pattern = tokens.map(t => this.escapeRegExp(t)).join('|')
      if (!pattern) return this.escapeHtml(text)
      const regex = new RegExp(pattern, 'gi')
      const matches = []
      let match
      while ((match = regex.exec(text)) !== null) {
        matches.push({ start: match.index, end: match.index + match[0].length })
        if (match.index === regex.lastIndex) regex.lastIndex += 1
      }
      if (!matches.length) {
        const fallback = text.length > 240 ? `${text.slice(0, 240)}...` : text
        return this.escapeHtml(fallback)
      }
      const contextBefore = 20
      const contextAfter = 36
      const ranges = matches
        .map(m => ({
          start: Math.max(0, m.start - contextBefore),
          end: Math.min(text.length, m.end + contextAfter)
        }))
        .sort((a, b) => a.start - b.start)
      const merged = []
      for (const range of ranges) {
        const last = merged[merged.length - 1]
        if (!last || range.start > last.end + 12) {
          merged.push({ ...range })
        } else {
          last.end = Math.max(last.end, range.end)
        }
      }
      const limited = merged.slice(0, 6)
      let snippet = ''
      limited.forEach((range, idx) => {
        if (idx === 0 && range.start > 0) snippet += '...'
        if (idx > 0 && range.start > limited[idx - 1].end) snippet += '...'
        snippet += this.highlightWithGroups(text.slice(range.start, range.end))
      })
      const lastRange = limited[limited.length - 1]
      if (lastRange && lastRange.end < text.length) snippet += '...'
      return snippet
    },
    openRawDialog(item) {
      this.activeRawItem = item || null
      this.showRawDialog = true
    },
    formatJson(obj) {
      try {
        return JSON.stringify(obj || {}, null, 2)
      } catch (e) {
        return '{}'
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.search-page {
  height: calc(100vh - 52px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.search-top {
  position: sticky;
  top: 0;
  z-index: 5;
  background: transparent;
  padding-bottom: 16px;
}
.page-hero {
  margin-bottom: 22px;
}
.page-hero-title {
  margin: 0;
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.03em;
  color: var(--qg-text-primary);
}
.page-hero-desc {
  margin: 10px 0 0;
  max-width: 560px;
  font-size: 15px;
  line-height: 1.65;
  color: var(--qg-text-secondary);
}
.filters-card {
  border-radius: var(--qg-radius-lg) !important;
  overflow: visible;
  :deep(.el-card__body) {
    padding: 20px 22px;
  }
}
.filter-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 18px;
  align-items: flex-end;
}
.query-item {
  flex: 1 1 280px;
  min-width: 0;
  :deep(.el-input__suffix) {
    right: 8px;
  }
  :deep(.el-input__suffix-inner) {
    display: inline-flex;
    align-items: center;
  }
}
.query-search-icon {
  cursor: pointer;
  color: var(--qg-text-secondary);
  transition: color 0.15s ease;
  &:hover {
    color: var(--qg-primary);
  }
}
.query-form {
  max-width: none;
  margin-bottom: 0;
}
.inline-form .el-form-item {
  margin-bottom: 0;
}
.compact-item {
  flex: 1 1 200px;
  :deep(.el-form-item__content) {
    line-height: 32px;
  }
}
.action-item {
  flex: 0 0 auto;
}
.filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.results-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding-right: 2px;
  padding-bottom: 24px;
}
.results-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  .results-title {
    font-size: 18px;
    font-weight: 600;
    color: var(--qg-text-primary);
    margin: 0;
  }
  .result-count {
    font-size: 13px;
    color: var(--qg-text-secondary);
  }
  .result-head-actions {
    display: inline-flex;
    align-items: center;
    gap: 14px;
  }
}
.result-item {
  margin-bottom: 16px;
  border-radius: var(--qg-radius-md) !important;
  :deep(.el-card__body) {
    padding: 20px 22px;
  }
  .result-meta {
    margin-bottom: 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    .meta-left {
      display: inline-flex;
      align-items: center;
      min-width: 0;
    }
    .meta-right {
      display: inline-flex;
      align-items: center;
    }
    .score {
      margin-left: 8px;
      color: var(--qg-text-secondary);
      font-size: 12px;
      white-space: nowrap;
    }
    .raw-btn {
      margin-left: 8px;
      padding: 0;
      font-size: 12px;
    }
    .doc-name {
      margin-left: 8px;
      font-size: 13px;
      color: var(--qg-text-primary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      max-width: min(420px, 100%);
    }
  }
  .result-content {
    font-size: 14px;
    color: var(--qg-text-primary);
    line-height: 1.75;
    .chunk-field {
      margin-bottom: 10px;
    }
    .field-key {
      color: var(--qg-text-secondary);
      margin-right: 4px;
    }
    .field-val :deep(mark) {
      color: var(--qg-text-primary);
      padding: 0 2px;
      border-radius: 3px;
    }
    .field-val :deep(.hl-group-0) {
      background: color-mix(in srgb, var(--qg-primary) 22%, transparent);
    }
    .field-val :deep(.hl-group-1) {
      background: color-mix(in srgb, var(--qg-primary-weak) 90%, var(--qg-primary));
    }
    .field-val :deep(.hl-group-2) {
      background: color-mix(in srgb, var(--qg-primary) 14%, var(--qg-bg-card-soft));
    }
    .field-val :deep(.hl-group-3) {
      background: color-mix(in srgb, var(--qg-primary) 18%, #f9a8d4);
    }
    .field-val :deep(.hl-group-4) {
      background: color-mix(in srgb, var(--qg-primary) 15%, #c4b5fd);
    }
    .field-val :deep(.hl-group-5) {
      background: color-mix(in srgb, var(--qg-primary) 12%, #fde68a);
    }
    .expand-row {
      margin-top: 4px;
    }
    .no-content {
      color: var(--qg-text-secondary);
      opacity: 0.8;
    }
  }
}
.advanced-body {
  padding: 0 16px;
}
.raw-dialog-body {
  .raw-meta {
    display: flex;
    align-items: center;
    margin-bottom: 10px;
  }
  .raw-score {
    margin-left: 8px;
    color: var(--qg-text-secondary);
    font-size: 12px;
  }
  .raw-doc {
    margin-left: 8px;
    color: var(--qg-text-primary);
    font-size: 13px;
  }
}
.raw-fields {
  max-height: 360px;
  overflow: auto;
}
.raw-field-row {
  padding: 8px 0;
  border-bottom: 1px solid var(--qg-border-subtle);
}
.raw-key {
  color: var(--qg-text-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}
.raw-val {
  color: var(--qg-text-primary);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.raw-json {
  margin: 0;
  background: var(--qg-code-bg);
  color: var(--qg-code-text);
  border-radius: var(--qg-radius-md);
  padding: 16px;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.55;
  border: 1px solid var(--qg-border-subtle);
}
@supports not (background: color-mix(in srgb, red 50%, blue)) {
  .result-item .result-content .field-val :deep(.hl-group-0) {
    background: rgba(64, 158, 255, 0.2);
  }
  .result-item .result-content .field-val :deep(.hl-group-1) {
    background: rgba(103, 194, 58, 0.22);
  }
  .result-item .result-content .field-val :deep(.hl-group-2) {
    background: rgba(144, 147, 153, 0.2);
  }
  .result-item .result-content .field-val :deep(.hl-group-3) {
    background: rgba(245, 108, 108, 0.18);
  }
  .result-item .result-content .field-val :deep(.hl-group-4) {
    background: rgba(155, 89, 246, 0.18);
  }
  .result-item .result-content .field-val :deep(.hl-group-5) {
    background: rgba(230, 162, 60, 0.22);
  }
}
@media (max-width: 1100px) {
  .search-page {
    height: auto;
    overflow: visible;
  }
  .search-top {
    position: static;
  }
  .results-panel {
    overflow: visible;
  }
}
</style>
