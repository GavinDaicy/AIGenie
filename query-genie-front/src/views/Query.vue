<template>
  <div class="query-page">
    <div class="query-header">
      <h1>智能检索与问答</h1>
      <p class="subtitle">支持关键字、语义、混合检索，以及基于知识库的智能问答</p>
    </div>
    <el-tabs v-model="activeTab" class="query-tabs">
      <el-tab-pane label="智能检索" name="search">
        <el-card class="query-card" shadow="hover">
          <el-form :model="form" label-width="100px" class="query-form">
            <el-form-item label="检索内容">
              <el-input
                v-model="form.query"
                type="textarea"
                :rows="2"
                placeholder="输入关键词或自然语言问句"
                clearable
                @keydown.enter.native.prevent="handleSearch"
              />
            </el-form-item>
            <el-form-item label="知识库">
              <el-select
                v-model="form.knowledgeCodes"
                multiple
                collapse-tags
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
            <el-form-item label="检索方式">
              <el-radio-group v-model="form.mode">
                <el-radio label="KEYWORD">关键字检索</el-radio>
                <el-radio label="VECTOR">语义检索</el-radio>
                <el-radio label="HYBRID">混合检索</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="返回条数">
              <el-input-number v-model="form.size" :min="1" :max="50" />
            </el-form-item>
            <el-form-item label="得分归一化">
              <el-switch
                v-model="form.normalizeScore"
                active-text="0~10"
                inactive-text="原始分"
              />
              <span class="form-tip">开启时分数归一化到 0~10，关闭时返回原始分便于对比</span>
            </el-form-item>
            <el-form-item label="Rerank 重排序">
              <el-switch
                v-model="form.rerank"
                active-text="开启"
                inactive-text="关闭"
              />
              <span class="form-tip">对初检结果用模型重排，提升 Top 结果相关性（需后端开启 rerank）</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" :loading="loading" @click="handleSearch">
                检索
              </el-button>
              <el-button @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <div v-if="results.length" class="results-section">
          <h3>检索结果（共 {{ results.length }} 条）</h3>
          <el-card v-for="(item, idx) in results" :key="idx" class="result-item" shadow="hover">
            <div class="result-meta">
              <el-tag size="small">{{ item.knowledgeCode }}</el-tag>
              <span class="score">相关度: {{ (item.score || 0).toFixed(4) }}</span>
              <span v-if="item.document" class="doc-name">{{ item.document.name }}</span>
            </div>
            <div class="result-content">
              <template v-if="item.chunkContent">
                <template v-for="(val, key) in item.chunkContent">
                  <div v-if="val != null && val !== ''" :key="key" class="chunk-field">
                    <span class="field-key">{{ key }}:</span>
                    <span class="field-val">{{ formatChunkValue(val) }}</span>
                  </div>
                </template>
              </template>
              <span v-else class="no-content">无分块内容</span>
            </div>
          </el-card>
        </div>
        <el-empty v-else-if="searched && !loading" description="暂无检索结果，请修改条件后重试" />
      </el-tab-pane>
      <el-tab-pane label="智能问答" name="qa">
        <el-card class="query-card" shadow="hover">
          <el-form :model="qaForm" label-width="100px" class="query-form">
            <el-form-item label="问题">
              <el-input
                v-model="qaForm.question"
                type="textarea"
                :rows="3"
                placeholder="输入您的问题，将基于知识库生成答案"
                clearable
              />
            </el-form-item>
            <el-form-item label="知识库">
              <el-select
                v-model="qaForm.knowledgeCodes"
                multiple
                collapse-tags
                placeholder="不选则基于全部已发布知识库"
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
            <el-form-item label="检索条数">
              <el-input-number v-model="qaForm.size" :min="1" :max="30" />
              <span class="form-tip">用于生成答案的参考条数</span>
            </el-form-item>
            <el-form-item label="Rerank 重排序">
              <el-switch v-model="qaForm.rerank" active-text="开启" inactive-text="关闭" />
            </el-form-item>
            <el-form-item label="Query 改写">
              <el-switch v-model="qaForm.enableQueryRewrite" active-text="启用" inactive-text="关闭" />
              <el-input-number
                v-model="qaForm.rewriteQueryCount"
                :min="1"
                :max="8"
                size="small"
                style="margin-left: 8px"
              />
              <span class="form-tip">总查询数 = 1 个主要 + (N-1) 个扩展</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-chat-dot-round" :loading="qaLoading" @click="handleAsk">
                提问
              </el-button>
              <el-button @click="resetQaForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <div v-if="qaSearched && !qaLoading && (qaAnswer || (qaSources && qaSources.length))" class="qa-result-section">
          <h3 v-if="qaAnswer">答案</h3>
          <el-card v-if="qaAnswer" class="qa-answer-card" shadow="hover">
            <div class="qa-answer-text">
              <template v-for="(seg, idx) in parseAnswerWithCitations(qaAnswer)">
                <span v-if="seg.type === 'text'" :key="idx">{{ seg.text }}</span>
                <sup
                  v-else
                  :key="'c-' + idx"
                  class="qa-citation"
                  @click="handleCitationClick(seg.index)"
                >
                  [{{ seg.index }}]
                </sup>
              </template>
            </div>
          </el-card>
          <div v-if="qaSources && qaSources.length" class="qa-sources-section">
            <h3>引用来源（{{ qaSources.length }} 条）</h3>
            <el-card v-for="(item, idx) in qaSources" :key="idx" class="result-item" shadow="hover">
              <div class="result-meta">
                <el-tag size="small">{{ item.knowledgeCode }}</el-tag>
                <span class="score">相关度: {{ (item.score || 0).toFixed(4) }}</span>
                <span v-if="item.documentName" class="doc-name">{{ item.documentName }}</span>
              </div>
              <div class="result-content">
                <template v-if="item.chunkContent && Object.keys(item.chunkContent).length">
                  <template v-for="(val, key) in item.chunkContent">
                    <div v-if="val != null && val !== ''" :key="key" class="chunk-field">
                      <span class="field-key">{{ key }}:</span>
                      <span class="field-val">{{ formatChunkValue(val) }}</span>
                    </div>
                  </template>
                </template>
                <span v-else class="no-content">无分块内容</span>
              </div>
            </el-card>
          </div>
        </div>
        <el-empty v-else-if="qaSearched && !qaLoading" description="未得到有效答案，请重试" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog
      title="引用来源详情"
      :visible.sync="showSourceDialog"
      width="600px"
    >
      <div v-if="activeSource">
        <div class="result-meta" style="margin-bottom: 8px;">
          <el-tag size="small">{{ activeSource.knowledgeCode }}</el-tag>
          <span class="score" style="margin-left: 8px; color: #909399; font-size: 12px;">
            相关度: {{ (activeSource.score || 0).toFixed(4) }}
          </span>
          <span v-if="activeSource.documentName" class="doc-name" style="margin-left: 8px; font-size: 13px; color: #606266;">
            {{ activeSource.documentName }}
          </span>
        </div>
        <div class="result-content">
          <template v-if="activeSource.chunkContent && Object.keys(activeSource.chunkContent).length">
            <div
              v-for="(val, key) in activeSource.chunkContent"
              v-show="val != null && val !== ''"
              :key="key"
              class="chunk-field"
            >
              <span class="field-key">{{ key }}:</span>
              <span class="field-val">{{ formatChunkValue(val) }}</span>
            </div>
          </template>
          <span v-else class="no-content">无分块内容</span>
        </div>
      </div>
      <div v-else>
        未找到来源详情。
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getKnowledgeList } from '@/api/knowledge'
import { search } from '@/api/query'
import { ask } from '@/api/qa'

export default {
  name: 'Query',
  data() {
    return {
      activeTab: 'search',
      knowledgeList: [],
      form: {
        query: '',
        knowledgeCodes: [],
        mode: 'HYBRID',
        size: 10,
        normalizeScore: true,
        rerank: false
      },
      loading: false,
      searched: false,
      results: [],
      qaForm: {
        question: '',
        knowledgeCodes: [],
        size: 10,
        rerank: true,
        enableQueryRewrite: true,
        rewriteQueryCount: 4
      },
      qaLoading: false,
      qaSearched: false,
      qaAnswer: '',
      qaSources: [],
      showSourceDialog: false,
      activeSource: null
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
          rerank: this.form.rerank
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
      this.form = { query: '', knowledgeCodes: [], mode: 'HYBRID', size: 10, normalizeScore: true, rerank: false }
      this.results = []
      this.searched = false
    },
    async handleAsk() {
      if (!this.qaForm.question.trim()) {
        this.$message.warning('请输入问题')
        return
      }
      this.qaLoading = true
      this.qaSearched = true
      this.qaAnswer = ''
      this.qaSources = []
      try {
        const payload = {
          question: this.qaForm.question.trim(),
          size: this.qaForm.size,
          rerank: this.qaForm.rerank,
          enableQueryRewrite: this.qaForm.enableQueryRewrite,
          rewriteQueryCount: this.qaForm.rewriteQueryCount
        }
        if (this.qaForm.knowledgeCodes.length) payload.knowledgeCodes = this.qaForm.knowledgeCodes
        const res = await ask(payload) || {}
        this.qaAnswer = res.answer || ''
        this.qaSources = res.sources || []
      } catch (e) {
        this.qaAnswer = ''
        this.qaSources = []
      } finally {
        this.qaLoading = false
      }
    },
    resetQaForm() {
      this.qaForm = { question: '', knowledgeCodes: [], size: 10, rerank: true, enableQueryRewrite: true, rewriteQueryCount: 4 }
      this.qaAnswer = ''
      this.qaSources = []
      this.qaSearched = false
    },
    formatChunkValue(val) {
      if (typeof val === 'string') return val
      if (typeof val === 'object' && val !== null) return JSON.stringify(val)
      return String(val)
    },
    parseAnswerWithCitations(text) {
      const segments = []
      if (!text) return segments
      const regex = /\[(\d+)\]/g
      let lastIndex = 0
      let match
      while ((match = regex.exec(text)) !== null) {
        const matchIndex = match.index
        if (matchIndex > lastIndex) {
          segments.push({
            type: 'text',
            text: text.slice(lastIndex, matchIndex)
          })
        }
        const num = parseInt(match[1], 10)
        segments.push({
          type: 'citation',
          index: Number.isNaN(num) ? null : num
        })
        lastIndex = matchIndex + match[0].length
      }
      if (lastIndex < text.length) {
        segments.push({
          type: 'text',
          text: text.slice(lastIndex)
        })
      }
      return segments
    },
    handleCitationClick(index) {
      if (!index || !Array.isArray(this.qaSources) || !this.qaSources.length) {
        this.$message && this.$message.warning('未找到对应来源')
        return
      }
      const source = this.qaSources[index - 1]
      if (!source) {
        this.$message && this.$message.warning('未找到对应来源')
        return
      }
      this.activeSource = source
      this.showSourceDialog = true
    }
  }
}
</script>

<style lang="scss" scoped>
.query-page { max-width: 900px; margin: 0 auto; }
.query-header {
  margin-bottom: 24px;
  h1 { font-size: 24px; color: #303133; margin: 0 0 8px 0; }
  .subtitle { color: #909399; font-size: 14px; margin: 0; }
}
.query-tabs { margin-bottom: 0; }
.query-card { margin-bottom: 24px; }
.query-form { max-width: 560px; }
.form-tip { margin-left: 8px; color: #909399; font-size: 12px; }
.results-section {
  h3 { font-size: 16px; color: #303133; margin-bottom: 12px; }
}
.qa-result-section {
  margin-top: 24px;
  h3 { font-size: 16px; color: #303133; margin-bottom: 12px; }
}
.qa-answer-card {
  margin-bottom: 20px;
  .qa-answer-text { font-size: 15px; color: #303133; line-height: 1.7; white-space: pre-wrap; }
}
.qa-sources-section {
  h3 { font-size: 16px; color: #303133; margin-bottom: 12px; }
}
.qa-citation {
  cursor: pointer;
  color: #409EFF;
  margin: 0 2px;
  user-select: none;
}
.result-item {
  margin-bottom: 12px;
  .result-meta {
    margin-bottom: 8px;
    .score { margin-left: 8px; color: #909399; font-size: 12px; }
    .doc-name { margin-left: 8px; font-size: 13px; color: #606266; }
  }
  .result-content {
    font-size: 14px; color: #303133; line-height: 1.6;
    .chunk-field { margin-bottom: 4px; }
    .field-key { color: #909399; margin-right: 6px; }
    .no-content { color: #c0c4cc; }
  }
}
</style>
