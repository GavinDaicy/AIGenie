<template>
  <el-drawer
    :visible.sync="visibleSync"
    :title="drawerTitle"
    direction="rtl"
    :size="drawerSize"
    :modal="true"
    :append-to-body="true"
    @close="$emit('update:visible', false)"
  >
    <div v-if="citation" class="cd-body">

      <!-- ───── KB 类型 ───── -->
      <template v-if="citation.type === 'KB'">
        <div class="cd-icon-row">
          <i class="el-icon-document" style="color:#67c23a;font-size:20px;margin-right:8px;"></i>
          <span class="cd-type-label">知识库引用</span>
        </div>
        <div class="cd-meta-list">
          <div class="cd-meta-row" v-if="citation.knowledgeCode">
            <span class="cd-meta-key">知识库</span>
            <span class="cd-meta-val">{{ citation.knowledgeCode }}</span>
          </div>
          <div class="cd-meta-row" v-if="citation.documentName">
            <span class="cd-meta-key">文档名称</span>
            <span class="cd-meta-val">{{ citation.documentName }}</span>
          </div>
          <div class="cd-meta-row" v-if="citation.score != null">
            <span class="cd-meta-key">相关度</span>
            <span class="cd-meta-val score">{{ (citation.score * 100).toFixed(1) }}%</span>
          </div>
        </div>
        <template v-if="citation.chunkContent && chunkText">
          <div class="cd-section-label">片段内容</div>
          <div class="cd-chunk-box">{{ chunkText }}</div>
        </template>
      </template>

      <!-- ───── SQL 类型 ───── -->
      <template v-else-if="citation.type === 'SQL'">
        <div class="cd-icon-row">
          <i class="el-icon-data-analysis" style="color:#e6a23c;font-size:20px;margin-right:8px;"></i>
          <span class="cd-type-label">数据库查询</span>
        </div>
        <div class="cd-section-label">执行 SQL</div>
        <pre class="cd-sql-block">{{ citation.sql }}</pre>
        <div v-if="tableData.length" class="cd-section-label">
          查询结果（{{ citation.rowCount || tableData.length }} 行）
        </div>
        <el-table
          v-if="tableData.length"
          :data="tableData"
          size="mini"
          border
          style="width:100%;margin-bottom:12px;"
          max-height="320"
        >
          <el-table-column
            v-for="col in (citation.columns || [])"
            :key="col"
            :prop="col"
            :label="col"
            show-overflow-tooltip
            min-width="80"
          />
        </el-table>
        <div class="cd-meta-list" v-if="citation.executionTimeMs">
          <div class="cd-meta-row">
            <span class="cd-meta-key">执行耗时</span>
            <span class="cd-meta-val">{{ citation.executionTimeMs }} ms</span>
          </div>
        </div>
      </template>

      <!-- ───── WEB 类型 ───── -->
      <template v-else-if="citation.type === 'WEB'">
        <div class="cd-icon-row">
          <i class="el-icon-search" style="color:#409eff;font-size:20px;margin-right:8px;"></i>
          <span class="cd-type-label">联网搜索</span>
        </div>
        <div class="cd-web-title" v-if="citation.title">{{ citation.title }}</div>
        <a
          v-if="citation.url"
          :href="citation.url"
          target="_blank"
          rel="noopener noreferrer"
          class="cd-web-url"
        >{{ citation.url }}</a>
        <div class="cd-meta-list">
          <div class="cd-meta-row" v-if="citation.source">
            <span class="cd-meta-key">来源</span>
            <span class="cd-meta-val">{{ citation.source }}</span>
          </div>
          <div class="cd-meta-row" v-if="citation.publishedTime">
            <span class="cd-meta-key">发布时间</span>
            <span class="cd-meta-val">{{ citation.publishedTime }}</span>
          </div>
        </div>
        <div class="cd-snippet" v-if="citation.snippet">{{ citation.snippet }}</div>
      </template>

    </div>
    <div v-else class="cd-empty">未找到引用内容</div>
  </el-drawer>
</template>

<script>
/**
 * 引用详情抽屉组件：根据 CitationItem.type 渲染 KB / SQL / WEB 三种样式。
 *
 * Props:
 *   visible  - Boolean（支持 .sync 修饰符）
 *   citation - CitationItem 对象
 *
 * Events:
 *   update:visible - 关闭时触发
 */
export default {
  name: 'CitationDrawer',
  props: {
    visible: { type: Boolean, default: false },
    citation: { type: Object, default: null }
  },
  computed: {
    visibleSync: {
      get() { return this.visible },
      set(v) { this.$emit('update:visible', v) }
    },
    drawerTitle() {
      if (!this.citation) return '引用详情'
      const idx = this.citation.index ? `[${this.citation.index}] ` : ''
      const typeMap = { KB: '知识库引用', SQL: '数据库查询', WEB: '联网搜索' }
      return idx + (typeMap[this.citation.type] || '引用详情')
    },
    drawerSize() {
      return this.citation && this.citation.type === 'SQL' ? '560px' : '420px'
    },
    chunkText() {
      if (!this.citation || !this.citation.chunkContent) return ''
      const cc = this.citation.chunkContent
      return Object.values(cc)
        .filter(v => v != null && String(v).trim())
        .join('\n')
    },
    tableData() {
      const c = this.citation
      if (!c || !c.columns || !c.rows || !c.rows.length) return []
      return c.rows.map(row => {
        const obj = {}
        c.columns.forEach((col, i) => { obj[col] = row[i] })
        return obj
      })
    }
  }
}
</script>

<style scoped>
.cd-body {
  padding: 4px 20px 20px;
}
.cd-icon-row {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--qg-border-subtle, #ebeef5);
}
.cd-type-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--qg-text-primary, #303133);
}
.cd-meta-list {
  margin-bottom: 14px;
}
.cd-meta-row {
  display: flex;
  align-items: baseline;
  margin-bottom: 6px;
  font-size: 13px;
}
.cd-meta-key {
  color: var(--qg-text-secondary, #909399);
  width: 72px;
  flex-shrink: 0;
}
.cd-meta-val {
  color: var(--qg-text-primary, #303133);
  word-break: break-all;
}
.cd-meta-val.score {
  color: #67c23a;
  font-weight: 600;
}
.cd-section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--qg-text-secondary, #909399);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 12px 0 6px;
}
.cd-chunk-box {
  background: var(--qg-bg-page, #f5f7fa);
  border: 1px solid var(--qg-border-subtle, #ebeef5);
  border-radius: 8px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--qg-text-primary, #303133);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
}
.cd-sql-block {
  background: #1e1e2e;
  color: #cdd6f4;
  border-radius: 8px;
  padding: 12px;
  font-size: 12.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  margin-bottom: 4px;
  overflow-x: auto;
}
.cd-web-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--qg-text-primary, #303133);
  margin-bottom: 6px;
  line-height: 1.5;
}
.cd-web-url {
  display: block;
  font-size: 12px;
  color: var(--qg-primary, #409eff);
  word-break: break-all;
  margin-bottom: 12px;
  text-decoration: none;
}
.cd-web-url:hover { text-decoration: underline; }
.cd-snippet {
  background: var(--qg-bg-page, #f5f7fa);
  border: 1px solid var(--qg-border-subtle, #ebeef5);
  border-radius: 8px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--qg-text-primary, #303133);
  white-space: pre-wrap;
  word-break: break-word;
}
.cd-empty {
  padding: 40px 20px;
  text-align: center;
  color: var(--qg-text-secondary, #909399);
  font-size: 14px;
}
</style>
