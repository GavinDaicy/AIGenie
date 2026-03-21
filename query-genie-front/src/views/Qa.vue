<template>
  <div class="qa-page">
    <aside
      class="qa-sidebar"
      v-show="!sidebarCollapsed"
      :style="{ width: sidebarWidth + 'px' }"
    >
      <div class="sidebar-brand">
        <span class="brand-mark">Q</span>
        <span class="brand-text">问答</span>
        <el-button
          type="text"
          class="sidebar-collapse-btn"
          icon="el-icon-arrow-left"
          title="收起会话历史"
          @click="toggleSidebar"
        />
      </div>
      <el-button
        type="primary"
        icon="el-icon-plus"
        class="new-session-btn"
        @click="handleNewSession"
      >
        新建对话
      </el-button>
      <div v-if="sessions.length" class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: currentSessionId === s.id }"
          @click="selectSession(s.id)"
        >
          <span class="session-title">{{ s.title || '新会话' }}</span>
          <el-button
            type="text"
            icon="el-icon-delete"
            class="session-delete"
            @click.stop="handleDeleteSession(s.id)"
          />
        </div>
      </div>
      <div v-else class="session-empty">暂无历史，点击上方开始</div>
    </aside>
    <div
      v-show="!sidebarCollapsed"
      class="qa-splitter"
      @mousedown.prevent="startResize"
      role="separator"
      aria-orientation="vertical"
      title="拖拽调整宽度"
    />
    <main class="qa-main">
      <template v-if="!currentSessionId">
        <div class="welcome-full">
          <div class="welcome-accent" aria-hidden="true" />
          <h1 class="welcome-title">Query Genie</h1>
          <p class="welcome-sub">基于知识库的多轮问答，检索与上下文一次到位</p>
          <div class="welcome-actions">
            <el-button type="primary" icon="el-icon-plus" @click="handleNewSession">
              新建对话
            </el-button>
            <el-button class="welcome-secondary" icon="el-icon-setting" @click="showSettingsDrawer = true">
              回答参数
            </el-button>
          </div>
          <p class="welcome-hint">可通过侧栏管理历史会话</p>
        </div>
      </template>
      <template v-else>
        <header class="chat-header">
          <span class="chat-header-title">{{ currentSessionTitle }}</span>
          <el-tooltip content="回答与检索参数" placement="bottom">
            <el-button
              type="text"
              class="header-icon-btn"
              icon="el-icon-setting"
              @click="showSettingsDrawer = true"
            />
          </el-tooltip>
        </header>
        <div ref="chatArea" class="chat-messages">
          <div
            v-if="messages.length === 0 && !qaLoading"
            class="chat-welcome-empty"
          >
            <p class="cwe-title">有问题，尽管问</p>
            <p class="cwe-desc">我会结合知识库作答，并保留本轮对话上下文</p>
          </div>
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="chat-row"
            :class="msg.role"
          >
            <div v-if="msg.role === 'assistant'" class="avatar avatar-bot" aria-hidden="true">答</div>
            <div class="bubble">
              <div v-if="msg.role === 'assistant' && !msg.content && qaLoading" class="bubble-content thinking-wrap">
                <div class="thinking-indicator">
                  <span class="thinking-label">正在思考</span>
                  <span class="thinking-dots">
                    <span class="dot"></span>
                    <span class="dot"></span>
                    <span class="dot"></span>
                  </span>
                </div>
                <div v-if="msg.rewrittenQueries && msg.rewrittenQueries.length" class="rewritten-queries-inline">
                  <span class="rewritten-label">检索用问句</span>
                  <div class="rewritten-tags">
                    <el-tag
                      v-for="(q, i) in msg.rewrittenQueries"
                      :key="i"
                      size="mini"
                      type="info"
                      effect="plain"
                      class="rewritten-tag"
                    >{{ q }}</el-tag>
                  </div>
                </div>
                <div class="thinking-wave">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
              <div v-else class="bubble-content">
                <template v-for="(seg, idx) in parseAnswerWithCitations(msg.content)">
                  <span v-if="seg.type === 'text'" :key="idx">{{ seg.text }}</span>
                  <sup
                    v-else
                    :key="'c-' + idx"
                    class="qa-citation"
                    @click="handleMessageCitationClick(msg, seg.index)"
                  >
                    [{{ seg.index }}]
                  </sup>
                </template>
              </div>
              <div v-if="msg.role === 'assistant' && msg.rewrittenQueries && msg.rewrittenQueries.length && (msg.content || !qaLoading)" class="bubble-rewritten">
                <el-collapse>
                  <el-collapse-item>
                    <template slot="title">
                      <span class="rewritten-collapse-title">检索用问句（{{ msg.rewrittenQueries.length }} 条）</span>
                    </template>
                    <ul class="rewritten-list">
                      <li v-for="(q, i) in msg.rewrittenQueries" :key="i" class="rewritten-item">{{ q }}</li>
                    </ul>
                  </el-collapse-item>
                </el-collapse>
              </div>  
              <div v-if="msg.role === 'assistant' && msg.sources && msg.sources.length" class="bubble-sources">
                <el-collapse>
                  <el-collapse-item>
                    <template slot="title">
                      <span class="sources-title">查看依据（{{ msg.sources.length }} 条）</span>
                    </template>
                    <div class="sources-list">
                      <div v-for="(item, idx) in msg.sources" :key="idx" class="source-card">
                        <div class="source-meta">
                          <el-tag size="small">{{ item.knowledgeCode }}</el-tag>
                          <span class="score">相关度: {{ (item.score || 0).toFixed(4) }}</span>
                          <span v-if="item.documentName" class="doc-name">{{ item.documentName }}</span>
                          <el-button type="text" class="source-link" @click="openSourceDrawer(item)">查看详情</el-button>
                        </div>
                        <div class="source-content">
                          <template v-if="item.chunkContent && Object.keys(item.chunkContent).length">
                            <div
                              v-for="(val, key) in item.chunkContent"
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
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="avatar avatar-me" aria-hidden="true">我</div>
          </div>
        </div>
        <div class="chat-input-wrap">
          <div class="composer">
            <el-input
              v-model="inputQuestion"
              class="composer-input"
              type="textarea"
              :rows="3"
              placeholder="发消息或输入问题…（Ctrl + Enter 发送）"
              resize="none"
              @keydown.native="onInputKeydown"
            />
            <div class="input-actions">
              <div class="actions-left">
                <el-select
                  v-model="qaForm.knowledgeCodes"
                  multiple
                  collapse-tags
                  placeholder="限定知识库（可选）"
                  size="small"
                  class="composer-knowledge"
                >
                  <el-option
                    v-for="kl in knowledgeList"
                    :key="kl.code"
                    :label="kl.name"
                    :value="kl.code"
                  />
                </el-select>
                <el-tooltip content="回答与检索参数" placement="top">
                  <el-button
                    circle
                    size="small"
                    class="icon-btn secondary"
                    icon="el-icon-setting"
                    @click="showSettingsDrawer = true"
                  />
                </el-tooltip>
              </div>
              <el-tooltip content="发送" placement="top">
                <el-button
                  circle
                  type="primary"
                  class="icon-btn send-btn"
                  :loading="qaLoading"
                  icon="el-icon-position"
                  @click="handleSend"
                />
              </el-tooltip>
            </div>
          </div>
        </div>
      </template>
    </main>
    <div
      v-show="sidebarCollapsed"
      class="qa-history-toggle"
      role="button"
      tabindex="0"
      @click="toggleSidebar"
      @keydown.enter="toggleSidebar"
      title="展开会话历史"
    >
      <i class="el-icon-arrow-right" aria-hidden="true" />
    </div>
    <el-drawer
      title="引用来源详情"
      :visible.sync="showSourceDrawer"
      size="560px"
      direction="rtl"
    >
      <div class="source-drawer-body">
        <div v-if="activeSource">
          <div class="source-drawer-meta">
            <el-tag size="small">{{ activeSource.knowledgeCode }}</el-tag>
            <span class="score">相关度: {{ (activeSource.score || 0).toFixed(4) }}</span>
            <span v-if="activeSource.documentName" class="doc-name">{{ activeSource.documentName }}</span>
          </div>
          <div class="source-content">
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
      </div>
    </el-drawer>
  <el-drawer
    title="问答参数设置"
    :visible.sync="showSettingsDrawer"
    size="380px"
    direction="rtl"
  >
    <div class="settings-body">
      <el-form label-position="top" size="small">
        <el-form-item label="检索条数">
          <el-input-number v-model="qaForm.size" :min="1" :max="30" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Rerank 重排序">
          <el-switch v-model="qaForm.rerank" active-text="开启" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="Query 改写">
          <el-switch v-model="qaForm.enableQueryRewrite" active-text="启用" inactive-text="关闭" />
        </el-form-item>
        <el-form-item label="改写总查询数">
          <el-input-number v-model="qaForm.rewriteQueryCount" :min="1" :max="8" style="width: 100%" />
        </el-form-item>
        <el-form-item label="输出方式">
          <el-radio-group v-model="qaForm.stream">
            <el-radio :label="true">流式</el-radio>
            <el-radio :label="false">一次性</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="时间衰减">
          <el-switch v-model="qaForm.useTimeDecay" active-text="开启" inactive-text="关闭" />
        </el-form-item>
      </el-form>
    </div>
  </el-drawer>
  </div>
</template>

<script>
import { getKnowledgeList } from '@/api/knowledge'
import { createSession, listSessions, getSession, deleteSession } from '@/api/session'
import { ask, askStream } from '@/api/qa'

export default {
  name: 'Qa',
  computed: {
    currentSessionTitle() {
      const s = this.sessions.find(x => x.id === this.currentSessionId)
      return s ? (s.title || '新会话') : '对话'
    }
  },
  data() {
    return {
      sessions: [],
      currentSessionId: null,
      messages: [],
      knowledgeList: [],
      inputQuestion: '',
      sidebarCollapsed: false,
      sidebarWidth: 240,
      sidebarMinWidth: 180,
      sidebarMaxWidth: 320,
      isResizingSidebar: false,
      resizeMoveHandler: null,
      resizeUpHandler: null,
      qaForm: {
        knowledgeCodes: [],
        size: 10,
        rerank: true,
        stream: true,
        enableQueryRewrite: true,
        rewriteQueryCount: 4,
        useTimeDecay: false
      },
      qaLoading: false,
      showSettingsDrawer: false,
      showSourceDrawer: false,
      activeSource: null
    }
  },
  created() {
    this.loadKnowledgeList()
    this.loadSessions()
    this.loadSidebarPrefs()
  },
  methods: {
    clamp(val, min, max) {
      return Math.max(min, Math.min(max, val))
    },
    loadSidebarPrefs() {
      try {
        const wRaw = window.localStorage.getItem('qg-qa-sidebar-width-v2')
        const collapsedRaw = window.localStorage.getItem('qg-qa-sidebar-collapsed-v2')

        const w = parseInt(wRaw, 10)
        if (!Number.isNaN(w)) this.sidebarWidth = this.clamp(w, this.sidebarMinWidth, this.sidebarMaxWidth)

        this.sidebarCollapsed = collapsedRaw === 'true'
      } catch (e) {
        // Ignore storage errors
      }
    },
    persistSidebarPrefs() {
      try {
        window.localStorage.setItem('qg-qa-sidebar-width-v2', String(this.sidebarWidth))
        window.localStorage.setItem('qg-qa-sidebar-collapsed-v2', String(this.sidebarCollapsed))
      } catch (e) {
        // Ignore storage errors
      }
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      this.persistSidebarPrefs()
      if (!this.sidebarCollapsed) {
        this.sidebarWidth = this.clamp(this.sidebarWidth, this.sidebarMinWidth, this.sidebarMaxWidth)
      }
    },
    stopResize() {
      if (!this.isResizingSidebar) return

      this.isResizingSidebar = false
      if (this.resizeMoveHandler) window.removeEventListener('mousemove', this.resizeMoveHandler)
      if (this.resizeUpHandler) window.removeEventListener('mouseup', this.resizeUpHandler)
      this.resizeMoveHandler = null
      this.resizeUpHandler = null

      document.body.style.cursor = ''
      document.body.style.userSelect = ''
      this.persistSidebarPrefs()
    },
    startResize(e) {
      // 在窄屏上不做拖拽，以免与滚动/输入冲突
      if (window.innerWidth < 900) return
      if (this.sidebarCollapsed) return

      const root = this.$el
      if (!root || !root.getBoundingClientRect) return

      const containerRect = root.getBoundingClientRect()
      const minW = this.sidebarMinWidth
      const maxW = this.sidebarMaxWidth

      this.isResizingSidebar = true
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'

      const moveHandler = (ev) => {
        const newW = ev.clientX - containerRect.left
        this.sidebarWidth = this.clamp(newW, minW, maxW)
      }

      const upHandler = () => {
        this.stopResize()
      }

      this.resizeMoveHandler = moveHandler
      this.resizeUpHandler = upHandler
      window.addEventListener('mousemove', moveHandler)
      window.addEventListener('mouseup', upHandler)
    },
    async loadKnowledgeList() {
      try {
        const list = await getKnowledgeList()
        this.knowledgeList = (list || []).filter(kl => kl.status === 2 || kl.status === 1)
      } catch (e) {
        this.knowledgeList = []
      }
    },
    async loadSessions() {
      try {
        this.sessions = await listSessions({ offset: 0, limit: 50 }) || []
      } catch (e) {
        this.sessions = []
      }
    },
    async handleNewSession() {
      try {
        const res = await createSession({})
        this.sessions.unshift({ id: res.sessionId, title: res.title || '新会话', updateTime: new Date() })
        this.currentSessionId = res.sessionId
        this.messages = []
      } catch (e) {
        this.$message.error('创建会话失败')
      }
    },
    async selectSession(id) {
      if (this.currentSessionId === id) return
      this.currentSessionId = id
      try {
        const detail = await getSession(id)
        this.messages = detail.messages || []
        const idx = this.sessions.findIndex(s => s.id === id)
        if (idx >= 0) this.sessions[idx].title = detail.title
        this.$nextTick(() => this.scrollToBottom())
      } catch (e) {
        this.messages = []
      }
    },
    async handleDeleteSession(id) {
      try {
        await this.$confirm('确定删除该会话？', '提示', { type: 'warning' })
        await deleteSession(id)
        this.sessions = this.sessions.filter(s => s.id !== id)
        if (this.currentSessionId === id) {
          this.currentSessionId = this.sessions.length ? this.sessions[0].id : null
          if (this.currentSessionId) this.selectSession(this.currentSessionId)
          else this.messages = []
        }
      } catch (e) {
        if (e !== 'cancel') this.$message.error('删除失败')
      }
    },
    async handleSend() {
      const question = this.inputQuestion.trim()
      if (!question) {
        this.$message.warning('请输入问题')
        return
      }
      if (!this.currentSessionId) {
        try {
          const res = await createSession({})
          this.currentSessionId = res.sessionId
          this.sessions.unshift({ id: res.sessionId, title: res.title || '新会话', updateTime: new Date() })
          this.messages = []
        } catch (e) {
          this.$message.error('创建会话失败')
          return
        }
      }

      this.messages.push({ id: 'u-' + Date.now(), role: 'user', content: question, sources: null })
      this.inputQuestion = ''
      this.qaLoading = true
      const assistantId = 'a-' + Date.now()
      this.messages.push({
        id: assistantId,
        role: 'assistant',
        content: '',
        sources: [],
        rewrittenQueries: null
      })
      this.$nextTick(() => this.scrollToBottom())

      const payload = {
        sessionId: this.currentSessionId,
        question,
        size: this.qaForm.size,
        rerank: this.qaForm.rerank,
        enableQueryRewrite: this.qaForm.enableQueryRewrite,
        rewriteQueryCount: this.qaForm.rewriteQueryCount,
        useTimeDecay: this.qaForm.useTimeDecay
      }
      if (this.qaForm.knowledgeCodes && this.qaForm.knowledgeCodes.length) {
        payload.knowledgeCodes = this.qaForm.knowledgeCodes
      }

      try {
        if (this.qaForm.stream) {
          let typewriterBuffer = ''
          let typewriterTimerId = null
          const INTERVAL_MS = 35
          const CHARS_PER_TICK = 2
          const flushTypewriter = () => {
            if (!typewriterBuffer) {
              if (typewriterTimerId) clearInterval(typewriterTimerId)
              typewriterTimerId = null
              return
            }
            const toShow = typewriterBuffer.slice(0, CHARS_PER_TICK)
            typewriterBuffer = typewriterBuffer.slice(CHARS_PER_TICK)
            const idx = this.messages.findIndex(m => m.id === assistantId)
            if (idx !== -1) {
              const msg = this.messages[idx]
              this.$set(this.messages[idx], 'content', (msg.content || '') + toShow)
              this.$nextTick(() => this.scrollToBottom())
            }
            if (!typewriterBuffer && typewriterTimerId) {
              clearInterval(typewriterTimerId)
              typewriterTimerId = null
            }
          }
          const res = await askStream(payload, {
            onChunk: (text) => {
              typewriterBuffer += text
              if (!typewriterTimerId) {
                typewriterTimerId = setInterval(flushTypewriter, INTERVAL_MS)
              }
            },
            onRewritten: (queries) => {
              const m = this.messages.find(x => x.id === assistantId)
              if (m) this.$set(m, 'rewrittenQueries', queries)
            }
          })
          const msg = this.messages.find(m => m.id === assistantId)
          if (msg) {
            msg.sources = res.sources || []
            if (res.rewrittenQueries && res.rewrittenQueries.length) {
              this.$set(msg, 'rewrittenQueries', res.rewrittenQueries)
            }
          }
        } else {
          const res = await ask(payload) || {}
          const msg = this.messages.find(m => m.id === assistantId)
          if (msg) {
            msg.content = res.answer || ''
            msg.sources = res.sources || []
            msg.rewrittenQueries = (res.rewrittenQueries && res.rewrittenQueries.length) ? res.rewrittenQueries : null
          }
        }
        const sidx = this.sessions.findIndex(s => s.id === this.currentSessionId)
        if (sidx >= 0 && this.messages.length === 2) {
          this.sessions[sidx].title = question.length > 20 ? question.substring(0, 20) + '...' : question
        }
        this.loadSessions()
      } catch (e) {
        const msg = this.messages.find(m => m.id === assistantId)
        if (msg) {
          msg.content = '回答失败，请重试。'
          msg.sources = []
        }
        this.$message.error('回答失败')
      } finally {
        this.qaLoading = false
        this.$nextTick(() => this.scrollToBottom())
      }
    },
    onInputKeydown(e) {
      if (e.key === 'Enter' && e.ctrlKey) {
        e.preventDefault()
        this.handleSend()
      }
    },
    scrollToBottom() {
      const el = this.$refs.chatArea
      if (el) el.scrollTop = el.scrollHeight
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
    handleMessageCitationClick(msg, index) {
      if (!index || !msg || !Array.isArray(msg.sources) || !msg.sources.length) {
        this.$message && this.$message.warning('未找到对应来源')
        return
      }
      const source = msg.sources[index - 1]
      if (!source) {
        this.$message && this.$message.warning('未找到对应来源')
        return
      }
      this.openSourceDrawer(source)
    },
    openSourceDrawer(source) {
      this.activeSource = source
      this.showSourceDrawer = true
    }
  }
}
</script>

<style lang="scss" scoped>
.qa-page {
  display: flex;
  gap: 0;
  height: calc(100vh - 52px);
  min-height: 420px;
  padding: 0;
  box-sizing: border-box;
  background: transparent;
  position: relative;
}
.qa-sidebar {
  flex: 0 0 auto;
  min-width: 180px;
  max-width: 320px;
  flex-shrink: 0;
  border-radius: 18px;
  padding: 14px 10px;
  background: var(--qg-bg-page) !important; /* 覆盖 App.vue 全局卡片阴影 */
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent),
    inset 1px 0 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent),
    inset -1px 0 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent);
  /* 盒阴影使用 inset：只表现上/左右侧边，不强调底部 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
  .sidebar-brand {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 4px 16px;
    margin-bottom: 2px;
    border-bottom: 1px solid var(--qg-border-subtle);
  }
  .brand-mark {
    width: 40px;
    height: 40px;
    border-radius: 12px;
    background: linear-gradient(145deg, var(--qg-primary) 0%, var(--qg-aside-active, var(--qg-primary)) 100%);
    color: #fff;
    font-weight: 700;
    font-size: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }
  .brand-text {
    font-size: 17px;
    font-weight: 600;
    color: var(--qg-text-primary);
    letter-spacing: 0.03em;
  }
  .new-session-btn {
    width: 100%;
    margin-bottom: 12px;
    font-weight: 500;
  }
  .session-list {
    flex: 1;
    overflow-y: auto;
    padding-top: 4px;
    margin: 0 -4px;
  }
  .session-item {
    padding: 9px 10px;
    border-radius: 9px;
    border-left: 3px solid transparent;
    margin-bottom: 4px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: space-between;
    transition: background 0.15s ease, color 0.15s ease;
    &:hover {
      background: transparent;
    }
    &.active {
      background: transparent !important;
      border-left-color: var(--qg-aside-active);
      color: var(--qg-primary);
      font-weight: 600;
    }
    .session-title {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 12px;
    }
    .session-delete {
      padding: 0 4px;
      opacity: 0;
      transition: opacity 0.15s ease;
    }
    &:hover .session-delete {
      opacity: 0.7;
    }
    &.active .session-delete {
      opacity: 0.7;
    }
  }
  .session-empty {
    color: var(--qg-text-secondary);
    font-size: 13px;
    padding: 24px 10px;
    text-align: center;
    line-height: 1.6;
  }
}
.qa-splitter {
  width: 8px;
  flex: 0 0 auto;
  cursor: col-resize;
  background: transparent;
  border-radius: 999px;
  margin: 0 -5px; // 让分隔条落在两侧卡片视觉边缘之间
  position: relative;
  transition: background 0.15s ease;

  &::before {
    content: '';
    position: absolute;
    left: 50%;
    top: 10px;
    bottom: 10px;
    width: 2px;
    transform: translateX(-50%);
    border-radius: 2px;
    background: rgba(90, 169, 255, 0.24);
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  &:hover::before {
    opacity: 1;
  }
}
.qa-history-toggle {
  position: absolute;
  top: 18px;
  left: 10px;
  width: 34px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: var(--qg-primary);
  transition: background 0.15s ease, border-color 0.15s ease;

  &:hover {
    background: rgba(90, 169, 255, 0.12);
    border-color: rgba(90, 169, 255, 0.35);
  }

  i {
    font-size: 18px;
  }
}
.sidebar-collapse-btn {
  margin-left: auto;
  opacity: 0.75;
  padding: 8px 6px;
  color: var(--qg-text-secondary);

  &:hover {
    opacity: 1;
    color: var(--qg-primary);
  }
}
.qa-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-radius: 16px;
  overflow: hidden;
  background: var(--qg-bg-page) !important; /* 覆盖 App.vue 全局卡片阴影 */
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent),
    inset -1px 0 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent);
}
.welcome-full {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 32px;
  text-align: center;
  position: relative;
}
.welcome-accent {
  width: 48px;
  height: 3px;
  border-radius: 2px;
  background: var(--qg-primary);
  opacity: 0.85;
  margin-bottom: 36px;
}
.welcome-title {
  margin: 0;
  font-size: 32px;
  font-weight: 600;
  color: var(--qg-text-primary);
  letter-spacing: -0.03em;
}
.welcome-sub {
  margin: 16px 0 0;
  max-width: 440px;
  font-size: 16px;
  line-height: 1.7;
  color: var(--qg-text-secondary);
  font-weight: 400;
}
.welcome-actions {
  margin-top: 40px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  justify-content: center;
}
.welcome-secondary {
  border-color: var(--qg-border-subtle);
  color: var(--qg-text-primary);
  background: var(--qg-bg-card-soft);
  &:hover {
    border-color: var(--qg-primary);
    color: var(--qg-primary);
    background: var(--qg-primary-weak);
  }
}
.welcome-hint {
  margin-top: 32px;
  font-size: 13px;
  color: var(--qg-text-secondary);
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 18px;
  border-bottom: 1px solid var(--qg-border-subtle);
  background: var(--qg-bg-card);
}
.chat-header-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--qg-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 16px;
}
.header-icon-btn {
  font-size: 18px;
  color: var(--qg-text-secondary);
  padding: 6px;
  &:hover {
    color: var(--qg-primary);
  }
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 22px 26px 16px;
  background: var(--qg-bg-page);
}
.chat-welcome-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 32px;
  text-align: center;
  .cwe-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: var(--qg-text-primary);
  }
  .cwe-desc {
    margin: 12px 0 0;
    font-size: 15px;
    color: var(--qg-text-secondary);
    max-width: 400px;
    line-height: 1.65;
  }
}
.chat-row {
  margin-bottom: 24px;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  &.assistant {
    justify-content: flex-start;
  }
  &.user {
    justify-content: flex-end;
  }
  .avatar {
    flex-shrink: 0;
    width: 38px;
    height: 38px;
    border-radius: 12px;
    font-size: 13px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 2px;
  }
  .avatar-bot {
    background: var(--qg-primary-weak);
    color: var(--qg-primary);
    border: 1px solid var(--qg-border-subtle);
  }
  .avatar-me {
    background: var(--qg-bg-card-soft);
    color: var(--qg-text-secondary);
    border: 1px solid var(--qg-border-subtle);
  }
  .bubble {
    max-width: min(760px, calc(100% - 56px));
    padding: 14px 18px;
    border-radius: var(--qg-radius-md);
    .bubble-content { font-size: 15px; line-height: 1.6; white-space: pre-wrap; }
    .thinking-wrap {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 10px;
      min-height: 24px;
      color: var(--qg-text-secondary);
    }
    .rewritten-queries-inline {
      width: 100%;
      margin-top: 6px;
      padding-top: 8px;
      border-top: 1px dashed var(--qg-border-subtle);
    }
    .rewritten-label {
      font-size: 12px;
      color: var(--qg-text-secondary);
      margin-right: 8px;
      vertical-align: middle;
    }
    .rewritten-tags {
      display: inline-flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: 4px;
    }
    .rewritten-tag {
      max-width: 280px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 12px;
    }
    .thinking-indicator {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .thinking-label {
      font-size: 14px;
      color: var(--qg-text-secondary);
    }
    .thinking-dots {
      display: inline-flex;
      gap: 4px;
      align-items: center;
    }
    .thinking-dots .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: var(--qg-primary);
      animation: thinking-bounce 1.4s ease-in-out infinite both;
    }
    .thinking-dots .dot:nth-child(1) { animation-delay: 0s; }
    .thinking-dots .dot:nth-child(2) { animation-delay: 0.16s; }
    .thinking-dots .dot:nth-child(3) { animation-delay: 0.32s; }
    .thinking-wave {
      display: inline-flex;
      gap: 3px;
      align-items: flex-end;
      height: 14px;
    }
    .thinking-wave span {
      width: 3px;
      border-radius: 2px;
      background: linear-gradient(180deg, var(--qg-primary) 0%, var(--qg-aside-active, var(--qg-primary)) 100%);
      animation: thinking-wave 0.8s ease-in-out infinite;
    }
    .thinking-wave span:nth-child(1) { height: 6px; animation-delay: 0s; }
    .thinking-wave span:nth-child(2) { height: 10px; animation-delay: 0.15s; }
    .thinking-wave span:nth-child(3) { height: 14px; animation-delay: 0.3s; }
  }
  @keyframes thinking-bounce {
    0%, 80%, 100% { transform: scale(0.6); opacity: 0.6; }
    40% { transform: scale(1); opacity: 1; }
  }
  @keyframes thinking-wave {
    0%, 100% { transform: scaleY(0.5); }
    50% { transform: scaleY(1); }
  }
  &.user .bubble {
    background: var(--qg-primary-weak);
    color: var(--qg-text-primary);
    border: 1px solid var(--qg-bubble-user-border);
    box-shadow: none;
  }
  &.assistant .bubble {
    background: var(--qg-bg-card);
    color: var(--qg-text-primary);
    border: 1px solid var(--qg-border-subtle);
    box-shadow: var(--qg-shadow);
  }
  .bubble-rewritten {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px solid var(--qg-border-subtle);
  }
  .rewritten-collapse-title {
    font-size: 12px;
    color: var(--qg-text-secondary);
  }
  .rewritten-list {
    margin: 0;
    padding-left: 18px;
    font-size: 13px;
    color: var(--qg-text-secondary);
    line-height: 1.6;
  }
  .rewritten-item {
    margin-bottom: 4px;
    word-break: break-word;
  }
  .bubble-sources { margin-top: 12px; }
  .sources-title {
    font-size: 13px;
    color: var(--qg-primary);
  }
  .sources-list {
    padding: 8px 0;
  }
  .source-card {
    padding: 12px;
    margin-bottom: 8px;
    background: var(--qg-bg-card-soft);
    border-radius: var(--qg-radius-md);
    font-size: 13px;
    border: 1px solid var(--qg-border-subtle);
    .source-meta {
      margin-bottom: 6px;
      display: flex;
      align-items: center;
      .score {
        margin-left: 8px;
        color: var(--qg-text-secondary);
      }
      .doc-name {
        margin-left: 8px;
      }
      .source-link {
        margin-left: auto;
      }
    }
    .chunk-field {
      margin-bottom: 4px;
    }
    .field-key {
      color: var(--qg-text-secondary);
      margin-right: 6px;
    }
    .no-content {
      color: var(--qg-text-secondary);
      opacity: 0.75;
    }
  }
}
.qa-citation {
  cursor: pointer;
  color: var(--qg-primary);
  margin: 0 2px;
  user-select: none;
}
.chat-input-wrap {
  padding: 12px 18px 10px;
  border-top: 1px solid var(--qg-border-subtle);
  background: var(--qg-bg-page);
  .composer {
    border: 1px solid var(--qg-border-subtle);
    border-radius: 16px;
    background: var(--qg-bg-card-soft);
    padding: 12px 14px 10px;
    max-width: 880px;
    margin: 0 auto;
    box-shadow: none !important; /* 覆盖 App.vue 全局阴影 */
  }
  .composer-input :deep(.el-textarea__inner) {
    border: none;
    box-shadow: none;
    padding: 8px 6px;
    font-size: 15px;
    line-height: 1.65;
    min-height: 72px !important;
    color: var(--qg-text-primary);
    background: transparent;
    &::placeholder {
      color: var(--qg-text-secondary);
      opacity: 0.65;
    }
  }
  .composer-input :deep(.el-textarea__inner:focus) {
    border: none;
    box-shadow: none;
  }
  .input-actions {
    margin-top: 8px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  .actions-left {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }
  .composer-knowledge {
    width: 280px;
    min-width: 220px;
  }
  .icon-btn {
    width: 36px;
    height: 36px;
    min-width: 36px;
    padding: 0;
    border-radius: 50%;
  }
  .icon-btn.secondary {
    border-color: var(--qg-border-subtle);
    color: var(--qg-text-secondary);
    &:hover {
      border-color: var(--qg-primary);
      color: var(--qg-primary);
      background: var(--qg-primary-weak);
    }
  }
  .send-btn {
    border: none;
    box-shadow: none;
  }
}
.settings-body {
  padding: 0 16px;
}
.source-drawer-body {
  padding: 0 16px;
}
.source-drawer-meta {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  .score {
    margin-left: 8px;
    color: var(--qg-text-secondary);
    font-size: 12px;
  }
  .doc-name {
    margin-left: 8px;
    font-size: 13px;
    color: var(--qg-text-primary);
  }
}
</style>
