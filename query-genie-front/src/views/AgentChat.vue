<template>
  <div class="agent-page">
    <!-- 侧边栏：会话历史 -->
    <aside class="qa-sidebar" v-show="!sidebarCollapsed" :style="{ width: sidebarWidth + 'px' }">
      <div class="sidebar-brand">
        <span class="brand-mark">A</span>
        <span class="brand-text">Agent</span>
        <el-button type="text" class="sidebar-collapse-btn" icon="el-icon-arrow-left" @click="toggleSidebar" />
      </div>
      <el-button type="primary" icon="el-icon-plus" class="new-session-btn" @click="handleNewSession">
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
          <el-button type="text" icon="el-icon-delete" class="session-delete" @click.stop="handleDeleteSession(s.id)" />
        </div>
      </div>
      <div v-else class="session-empty">暂无历史，点击上方开始</div>
    </aside>

    <!-- 分隔拖拽条 -->
    <div v-show="!sidebarCollapsed" class="qa-splitter" @mousedown.prevent="startResize" />

    <!-- 主内容区 -->
    <main class="qa-main">
      <!-- 欢迎页 -->
      <template v-if="!currentSessionId">
        <div class="welcome-full">
          <div class="welcome-accent" />
          <h1 class="welcome-title">Agent 智能助手</h1>
          <p class="welcome-sub">多步推理 · 知识检索 · 数据分析<br>自动选择工具，给出有据可查的答案</p>
          <div class="welcome-actions">
            <el-button type="primary" icon="el-icon-plus" @click="handleNewSession">新建对话</el-button>
          </div>
        </div>
      </template>

      <!-- 对话区 -->
      <template v-else>
        <header class="chat-header">
          <span class="chat-header-title">{{ currentSessionTitle }}</span>
        </header>

        <div ref="chatArea" class="chat-messages">
          <div v-if="messages.length === 0 && !agentLoading" class="chat-welcome-empty">
            <p class="cwe-title">有问题，尽管问</p>
            <p class="cwe-desc">Agent 会自动查询知识库和数据库，给出综合答案</p>
          </div>

          <div v-for="msg in messages" :key="msg.id" class="chat-row" :class="msg.role">
            <div v-if="msg.role === 'assistant'" class="avatar avatar-bot">A</div>
            <div class="bubble">
              <!-- 推理中 loading -->
              <div v-if="msg.role === 'assistant' && msg.loading" class="bubble-content thinking-wrap">
                <div class="thinking-indicator">
                  <span class="thinking-label">Agent 正在推理</span>
                  <span class="thinking-dots">
                    <span class="dot" /><span class="dot" /><span class="dot" />
                  </span>
                </div>
                <div class="thinking-wave"><span /><span /><span /></div>
              </div>

              <!-- 推理步骤面板 -->
              <div v-if="msg.role === 'assistant' && msg.steps && msg.steps.length" class="step-panel">
                <el-collapse v-model="msg.stepsExpanded">
                  <el-collapse-item name="steps">
                    <template slot="title">
                      <span class="step-panel-title">
                        <i class="el-icon-cpu" />
                        推理步骤（{{ msg.steps.length }} 步）
                        <el-tag v-if="msg.loading" size="mini" type="warning" effect="plain" class="step-badge">进行中</el-tag>
                        <el-tag v-else size="mini" type="success" effect="plain" class="step-badge">已完成</el-tag>
                      </span>
                    </template>
                    <div class="step-list">
                      <div v-for="(step, idx) in msg.steps" :key="idx" class="step-item" :class="'step-' + step.type.toLowerCase()">
                        <!-- ROUTING -->
                        <template v-if="step.type === 'ROUTING'">
                          <div class="step-header">
                            <span class="step-icon routing-icon">🎯</span>
                            <span class="step-label">意图识别：
                              <el-tag size="mini" :type="getRoutingTagType(step.params)" effect="plain" class="routing-tag">
                                {{ step.content }}
                              </el-tag>
                            </span>
                          </div>
                        </template>
                        <!-- PLANNING -->
                        <template v-else-if="step.type === 'PLANNING'">
                          <div class="step-header">
                            <span class="step-icon planning-icon">📝</span>
                            <span class="step-label planning-label">{{ step.content }}</span>
                          </div>
                        </template>
                        <!-- THINKING -->
                        <template v-else-if="step.type === 'THINKING'">
                          <div class="step-header">
                            <span class="step-icon thinking-icon">⚙️</span>
                            <span class="step-label">推理中（第 {{ step.iteration }} 轮）</span>
                            <span class="step-thinking-dots">
                              <span class="dot" /><span class="dot" /><span class="dot" />
                            </span>
                          </div>
                        </template>
                        <!-- THOUGHT -->
                        <template v-else-if="step.type === 'THOUGHT'">
                          <div class="step-header">
                            <span class="step-icon thought-icon">💭</span>
                            <span class="step-label">思考（第 {{ step.iteration }} 轮）</span>
                          </div>
                          <div class="step-content thought-text">
                            {{ step.content }}<span v-if="step._streaming" class="stream-cursor">▌</span>
                          </div>
                        </template>
                        <!-- TOOL_CALL -->
                        <template v-else-if="step.type === 'TOOL_CALL'">
                          <div class="step-header">
                            <span class="step-icon tool-icon">🔧</span>
                            <span class="step-label">调用工具 <code class="tool-name">{{ step.toolName }}</code></span>
                          </div>
                          <div v-if="step.params" class="step-content">
                            <pre class="step-code">{{ formatJson(step.params) }}</pre>
                          </div>
                        </template>
                        <!-- TOOL_RESULT -->
                        <template v-else-if="step.type === 'TOOL_RESULT'">
                          <div class="step-header">
                            <span class="step-icon result-icon">📋</span>
                            <span class="step-label">
                              工具返回 <code class="tool-name">{{ step.toolName }}</code>
                              <span v-if="step.durationMs" class="step-duration">{{ step.durationMs }}ms</span>
                            </span>
                          </div>
                          <div class="step-content">
                            <pre class="step-code result-code">{{ truncate(step.content, 600) }}</pre>
                            <!-- I4-F2: searchWeb 来源链接 -->
                            <div v-if="step.toolName === 'searchWeb'" class="web-source-links">
                              <span class="source-links-label">来源：</span>
                              <a
                                v-for="(url, ui) in extractUrls(step.content)"
                                :key="ui"
                                :href="url"
                                target="_blank"
                                rel="noopener noreferrer"
                                class="source-link"
                              >{{ ui + 1 }}</a>
                            </div>
                          </div>
                        </template>
                        <!-- ERROR -->
                        <template v-else-if="step.type === 'ERROR'">
                          <div class="step-header">
                            <span class="step-icon error-icon">⚠️</span>
                            <span class="step-label error-label">错误</span>
                          </div>
                          <div class="step-content error-text">{{ step.content }}</div>
                        </template>
                      </div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>

              <!-- 用户消息内容 -->
              <div v-if="msg.role === 'user'" class="bubble-content">
                {{ msg.content }}
              </div>

              <!-- I4-F1: ASK_USER 追问气泡 -->
              <div v-if="msg.role === 'assistant' && msg.isWaitingForUser" class="ask-user-bubble">
                <div class="ask-user-question">
                  <span class="ask-user-icon">❓</span>
                  {{ msg.askUserQuestion }}
                </div>
                <div v-if="!msg.askUserAnswered" class="ask-user-reply-area">
                  <el-input
                    v-model="msg.askUserReply"
                    size="small"
                    placeholder="请输入您的回复…"
                    class="ask-user-input"
                    @keydown.native.enter.prevent="submitAskUserReply(msg)"
                  />
                  <el-button type="warning" size="small" @click="submitAskUserReply(msg)">回复</el-button>
                </div>
                <div v-else class="ask-user-answered">
                  <span class="ask-user-answered-label">您的回复：</span>{{ msg.askUserAnsweredText }}
                </div>
              </div>

              <!-- 最终答案 -->
              <div v-if="msg.role === 'assistant' && (msg.content || !msg.loading)" class="bubble-content">
                <span v-if="!msg.content && !msg.loading" class="empty-answer">（无结果）</span>
                <citation-text
                  v-else
                  :text="msg.content"
                  :citations="msg.citations || []"
                  @cite-click="openCitationDrawer"
                />
                <span v-if="msg.streaming" class="cursor-blink">|</span>
              </div>
            </div>
            <div v-if="msg.role === 'user'" class="avatar avatar-me">我</div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input-wrap">
          <div class="composer">
            <el-input
              v-model="inputQuestion"
              class="composer-input"
              type="textarea"
              :rows="3"
              placeholder="发消息或输入问题… （Ctrl + Enter 发送）"
              resize="none"
              @keydown.native="onInputKeydown"
            />
            <div class="input-actions">
              <div class="actions-left">
                <el-select
                  v-model="agentForm.knowledgeCodes"
                  multiple collapse-tags
                  placeholder="限定知识库（可选）"
                  size="small"
                  class="composer-select"
                >
                  <el-option v-for="kl in knowledgeList" :key="kl.code" :label="kl.name" :value="kl.code" />
                </el-select>
                <el-select
                  v-model="agentForm.datasourceIds"
                  multiple collapse-tags
                  placeholder="限定数据源（可选）"
                  size="small"
                  class="composer-select"
                >
                  <el-option v-for="ds in datasourceList" :key="ds.id" :label="ds.name" :value="ds.id" />
                </el-select>
              </div>
              <el-tooltip content="发送（Ctrl+Enter）" placement="top">
                <el-button
                  circle type="primary"
                  class="icon-btn send-btn"
                  :loading="agentLoading"
                  icon="el-icon-position"
                  @click="handleSend"
                />
              </el-tooltip>
            </div>
          </div>
        </div>
      </template>
    </main>

    <!-- 侧栏收起时的展开按钮 -->
    <div v-show="sidebarCollapsed" class="qa-history-toggle" role="button" tabindex="0"
      @click="toggleSidebar" @keydown.enter="toggleSidebar">
      <i class="el-icon-arrow-right" />
    </div>
    <citation-drawer
      v-if="citationDrawerVisible"
      :visible.sync="citationDrawerVisible"
      :citation="activeCitation"
    />
  </div>
</template>

<script>
import { agentAskStream } from '@/api/agent'
import { getKnowledgeList } from '@/api/knowledge'
import { listDatasources } from '@/api/schema'
import { createSession, listSessions, getSession, deleteSession } from '@/api/session'
import CitationText from '@/components/CitationText.vue'
import CitationDrawer from '@/components/CitationDrawer.vue'

export default {
  name: 'AgentChat',
  components: { CitationText, CitationDrawer },
  computed: {
    currentSessionTitle() {
      const s = this.sessions.find(x => x.id === this.currentSessionId)
      return s ? (s.title || '新会话') : 'Agent 对话'
    }
  },
  data() {
    return {
      sessions: [],
      currentSessionId: null,
      messages: [],
      knowledgeList: [],
      datasourceList: [],
      inputQuestion: '',
      sidebarCollapsed: false,
      sidebarWidth: 240,
      sidebarMinWidth: 180,
      sidebarMaxWidth: 320,
      isResizingSidebar: false,
      resizeMoveHandler: null,
      resizeUpHandler: null,
      agentForm: {
        knowledgeCodes: [],
        datasourceIds: []
      },
      agentLoading: false,
      abortFn: null,
      citationDrawerVisible: false,
      activeCitation: null
    }
  },
  created() {
    this.loadKnowledgeList()
    this.loadDatasourceList()
    this.loadSessions()
    this.loadSidebarPrefs()
  },
  beforeDestroy() {
    if (this.abortFn) this.abortFn()
  },
  methods: {
    clamp(v, min, max) { return Math.max(min, Math.min(max, v)) },
    loadSidebarPrefs() {
      try {
        const w = parseInt(window.localStorage.getItem('qg-agent-sidebar-width'), 10)
        if (!Number.isNaN(w)) this.sidebarWidth = this.clamp(w, this.sidebarMinWidth, this.sidebarMaxWidth)
        this.sidebarCollapsed = window.localStorage.getItem('qg-agent-sidebar-collapsed') === 'true'
      } catch (_) {}
    },
    persistSidebarPrefs() {
      try {
        window.localStorage.setItem('qg-agent-sidebar-width', String(this.sidebarWidth))
        window.localStorage.setItem('qg-agent-sidebar-collapsed', String(this.sidebarCollapsed))
      } catch (_) {}
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      this.persistSidebarPrefs()
    },
    startResize(e) {
      if (window.innerWidth < 900 || this.sidebarCollapsed) return
      const rect = this.$el.getBoundingClientRect()
      this.isResizingSidebar = true
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
      const move = (ev) => { this.sidebarWidth = this.clamp(ev.clientX - rect.left, this.sidebarMinWidth, this.sidebarMaxWidth) }
      const up = () => {
        this.isResizingSidebar = false
        document.body.style.cursor = ''
        document.body.style.userSelect = ''
        window.removeEventListener('mousemove', move)
        window.removeEventListener('mouseup', up)
        this.persistSidebarPrefs()
      }
      this.resizeMoveHandler = move
      this.resizeUpHandler = up
      window.addEventListener('mousemove', move)
      window.addEventListener('mouseup', up)
    },
    async loadKnowledgeList() {
      try {
        const list = await getKnowledgeList()
        this.knowledgeList = (list || []).filter(kl => kl.status === 2 || kl.status === 1)
      } catch (_) { this.knowledgeList = [] }
    },
    async loadDatasourceList() {
      try {
        const res = await listDatasources()
        this.datasourceList = (res && res.list) ? res.list : (Array.isArray(res) ? res : [])
      } catch (_) { this.datasourceList = [] }
    },
    async loadSessions() {
      try {
        this.sessions = await listSessions({ offset: 0, limit: 50 }) || []
        // 恢复上次的会话
        const lastId = window.localStorage.getItem('qg-agent-session-id')
        if (lastId && this.sessions.find(s => s.id === lastId)) {
          await this.selectSession(lastId)
        }
      } catch (_) { this.sessions = [] }
    },
    async handleNewSession() {
      try {
        const res = await createSession({})
        this.sessions.unshift({ id: res.sessionId, title: '新会话' })
        this.currentSessionId = res.sessionId
        this.messages = []
        window.localStorage.setItem('qg-agent-session-id', res.sessionId)
      } catch (_) { this.$message.error('创建会话失败') }
    },
    async selectSession(id) {
      if (this.currentSessionId === id) return
      if (this.abortFn) { this.abortFn(); this.abortFn = null }
      this.currentSessionId = id
      this.messages = []
      this.agentLoading = false
      window.localStorage.setItem('qg-agent-session-id', id)
      try {
        const detail = await getSession(id)
        this.messages = (detail.messages || []).map(m => ({
          ...m,
          citations: m.citations || [],
          sources: m.sources || [],
          steps: m.steps || [],
          stepsExpanded: m.steps && m.steps.length ? [] : ['steps'],
          loading: false,
          streaming: false
        }))
        this.$nextTick(() => this.scrollToBottom())
      } catch (_) {
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
          this.messages = []
        }
      } catch (e) { if (e !== 'cancel') this.$message.error('删除失败') }
    },

    async handleSend() {
      const question = this.inputQuestion.trim()
      if (!question) { this.$message.warning('请输入问题'); return }
      if (!this.currentSessionId) {
        try {
          const res = await createSession({})
          this.currentSessionId = res.sessionId
          this.sessions.unshift({ id: res.sessionId, title: '新会话' })
          this.messages = []
        } catch (_) { this.$message.error('创建会话失败'); return }
      }

      // 推入用户消息
      this.messages.push({ id: 'u-' + Date.now(), role: 'user', content: question })
      this.inputQuestion = ''
      this.agentLoading = true
      this.$nextTick(() => this.scrollToBottom())

      // 创建 assistant 消息占位
      const assistantId = 'a-' + Date.now()
      this.messages.push({
        id: assistantId,
        role: 'assistant',
        content: '',
        steps: [],
        stepsExpanded: ['steps'],
        loading: true,
        streaming: false,
        isWaitingForUser: false,
        askUserQuestion: '',
        askUserReply: '',
        askUserAnswered: false,
        askUserAnsweredText: '',
        sources: [],
        citations: []
      })
      this.$nextTick(() => this.scrollToBottom())

      const params = {
        question,
        sessionId: this.currentSessionId,
        knowledgeCodes: this.agentForm.knowledgeCodes.length ? this.agentForm.knowledgeCodes : undefined,
        datasourceIds: this.agentForm.datasourceIds.length ? this.agentForm.datasourceIds : undefined
      }

      this.abortFn = agentAskStream(params, {
        onStep: (event) => {
          const msg = this.messages.find(m => m.id === assistantId)
          if (!msg) return

          if (event.type === 'CITATIONS') {
            msg.citations = event.citations || []
          } else if (event.type === 'FINAL_ANSWER') {
            // 最终答案：将正在流式输出的 THOUGHT 标记为完成
            msg.steps.forEach(s => { if (s._streaming) s._streaming = false })
            msg.loading = false
            msg.streaming = true
            this.typewriterAppend(msg, event.content || '')
          } else if (event.type === 'ASK_USER') {
            // 追问事件：显示追问气泡，暂停等待用户回复
            msg.steps.forEach(s => { if (s._streaming) s._streaming = false })
            msg.loading = false
            msg.isWaitingForUser = true
            msg.askUserQuestion = event.content || ''
            msg.askUserReply = ''
            msg.askUserAnswered = false
          } else if (event.type === 'THOUGHT_CHUNK') {
            // 流式 Token：找到当前轮正在流式的 THOUGHT 步骤
            let thoughtStep = msg.steps.find(
              s => s.type === 'THOUGHT' && s.iteration === event.iteration && s._streaming
            )
            if (!thoughtStep) {
              // 移除同轮次的 THINKING 占位步骤
              const thinkIdx = msg.steps.findIndex(
                s => s.type === 'THINKING' && s.iteration === event.iteration
              )
              if (thinkIdx >= 0) msg.steps.splice(thinkIdx, 1)
              thoughtStep = {
                type: 'THOUGHT',
                iteration: event.iteration,
                content: '',
                _streaming: true
              }
              msg.steps.push(thoughtStep)
            }
            thoughtStep.content = (thoughtStep.content || '') + (event.content || '')
          } else if (event.type === 'TOOL_CALL' || event.type === 'TOOL_RESULT') {
            // 工具调用/返回：标记当前轮思考完成
            msg.steps.forEach(s => { if (s._streaming && s.iteration === event.iteration) s._streaming = false })
            msg.steps.push(event)
          } else {
            msg.steps.push(event)
          }
          this.$nextTick(() => this.scrollToBottom())
        },
        onChunk: (text) => {
          const msg = this.messages.find(m => m.id === assistantId)
          if (!msg) return
          msg.loading = false
          msg.streaming = true
          this.typewriterAppend(msg, text)
          this.$nextTick(() => this.scrollToBottom())
        },
        onDone: () => {
          const msg = this.messages.find(m => m.id === assistantId)
          if (msg) {
            msg.loading = false
            msg.streaming = false
            msg.steps.forEach(s => { if (s._streaming) s._streaming = false })
          }
          this.agentLoading = false
          this.abortFn = null
          // 更新会话标题
          const sidx = this.sessions.findIndex(s => s.id === this.currentSessionId)
          if (sidx >= 0 && this.messages.length === 2) {
            this.sessions[sidx].title = question.length > 20 ? question.substring(0, 20) + '...' : question
          }
          this.$nextTick(() => this.scrollToBottom())
        },
        onError: (err) => {
          const msg = this.messages.find(m => m.id === assistantId)
          if (msg) {
            msg.loading = false
            msg.streaming = false
            if (!msg.content) msg.content = '请求失败，请重试。'
          }
          this.agentLoading = false
          this.abortFn = null
          this.$message.error(err && err.message ? err.message : 'Agent 请求失败')
        }
      })
    },

    typewriterAppend(msg, text) {
      if (!text) return
      msg._typeBuffer = (msg._typeBuffer || '') + text
      if (msg._typeTimer) return
      msg._typeTimer = setInterval(() => {
        if (!msg._typeBuffer) {
          clearInterval(msg._typeTimer)
          msg._typeTimer = null
          return
        }
        const chunk = msg._typeBuffer.slice(0, 3)
        msg._typeBuffer = msg._typeBuffer.slice(3)
        // 直接赋值已有属性（Vue 2 响应式），不替换对象引用
        msg.content = (msg.content || '') + chunk
        this.$nextTick(() => this.scrollToBottom())
      }, 30)
    },

    submitAskUserReply(msg) {
      const reply = (msg.askUserReply || '').trim()
      if (!reply || this.agentLoading) return
      msg.askUserAnswered = true
      msg.askUserAnsweredText = reply
      msg.askUserReply = ''
      this.inputQuestion = reply
      this.handleSend()
    },
    openCitationDrawer(citation) {
      this.activeCitation = citation
      this.citationDrawerVisible = true
    },
    onInputKeydown(e) {
      if (e.key === 'Enter' && e.ctrlKey) { e.preventDefault(); this.handleSend() }
    },
    scrollToBottom() {
      const el = this.$refs.chatArea
      if (el) el.scrollTop = el.scrollHeight
    },
    formatJson(str) {
      try { return JSON.stringify(JSON.parse(str), null, 2) } catch (_) { return str }
    },
    extractUrls(text) {
      if (!text) return []
      const urlRegex = /(https?:\/\/[^\s"'<>]+)/g
      return [...new Set(text.match(urlRegex) || [])]
    },
    truncate(str, len) {
      if (!str) return ''
      return str.length > len ? str.slice(0, len) + '\n…（内容已截断）' : str
    },
    getRoutingTagType(questionType) {
      if (questionType === 'DATA_QUERY') return 'warning'
      if (questionType === 'KNOWLEDGE') return 'success'
      return 'info'
    }
  }
}
</script>

<style lang="scss" scoped>
.agent-page {
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
  border-radius: 18px;
  padding: 14px 10px;
  background: var(--qg-bg-page) !important;
  box-shadow: inset 0 1px 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent),
    inset 1px 0 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent),
    inset -1px 0 0 color-mix(in srgb, var(--qg-border-subtle) 65%, transparent);
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
    background: linear-gradient(145deg, #8b5cf6 0%, #3b82f6 100%);
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
    transition: background 0.15s ease;
    &.active {
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
    &:hover .session-delete, &.active .session-delete { opacity: 0.7; }
  }
  .session-empty {
    color: var(--qg-text-secondary);
    font-size: 13px;
    padding: 24px 10px;
    text-align: center;
  }
}
.qa-splitter {
  width: 8px;
  flex: 0 0 auto;
  cursor: col-resize;
  background: transparent;
  border-radius: 999px;
  margin: 0 -5px;
  position: relative;
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
  &:hover::before { opacity: 1; }
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
  transition: background 0.15s ease;
  &:hover { background: rgba(90, 169, 255, 0.12); }
  i { font-size: 18px; }
}
.sidebar-collapse-btn {
  margin-left: auto;
  opacity: 0.75;
  padding: 8px 6px;
  color: var(--qg-text-secondary);
  &:hover { opacity: 1; color: var(--qg-primary); }
}
.qa-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  border-radius: 16px;
  overflow: hidden;
  background: var(--qg-bg-page) !important;
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
}
.welcome-accent {
  width: 48px;
  height: 3px;
  border-radius: 2px;
  background: linear-gradient(90deg, #8b5cf6, #3b82f6);
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
}
.welcome-actions {
  margin-top: 40px;
  display: flex;
  gap: 14px;
}
.chat-header {
  display: flex;
  align-items: center;
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
  .cwe-title { margin: 0; font-size: 20px; font-weight: 600; color: var(--qg-text-primary); }
  .cwe-desc { margin: 12px 0 0; font-size: 15px; color: var(--qg-text-secondary); max-width: 400px; line-height: 1.65; }
}
.chat-row {
  margin-bottom: 24px;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  &.assistant { justify-content: flex-start; }
  &.user { justify-content: flex-end; }

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
  }
  .avatar-bot {
    background: linear-gradient(145deg, #8b5cf6 0%, #3b82f6 100%);
    color: #fff;
  }
  .avatar-me {
    background: var(--qg-primary-weak);
    color: var(--qg-primary);
    border: 1px solid var(--qg-bubble-user-border);
  }
  .bubble {
    max-width: 75%;
    min-width: 40px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  &.user .bubble {
    align-items: flex-end;
    .bubble-content {
      background: var(--qg-primary-weak);
      border: 1px solid var(--qg-bubble-user-border);
      color: var(--qg-text-primary);
      border-radius: 16px 4px 16px 16px;
    }
  }
}
.bubble-content {
  padding: 12px 16px;
  border-radius: 4px 16px 16px 16px;
  background: var(--qg-bg-card);
  color: var(--qg-text-primary);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  white-space: pre-wrap;
}
.thinking-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  .thinking-label { font-size: 13px; color: var(--qg-text-secondary); }
}
.thinking-dots {
  display: flex;
  gap: 4px;
  .dot {
    width: 5px; height: 5px; border-radius: 50%;
    background: var(--qg-primary);
    animation: blink 1.2s infinite both;
    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}
.thinking-wave {
  display: flex;
  gap: 3px;
  height: 16px;
  align-items: flex-end;
  span {
    width: 3px;
    border-radius: 2px;
    background: var(--qg-primary);
    animation: wave 1.2s ease-in-out infinite;
    &:nth-child(1) { animation-delay: 0s; }
    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}
@keyframes blink {
  0%, 80%, 100% { opacity: 0.2; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}
@keyframes wave {
  0%, 100% { height: 4px; }
  50% { height: 14px; }
}

/* 推理步骤面板 */
.step-panel {
  background: var(--qg-bg-card);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  overflow: hidden;
}
.step-panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--qg-text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
  i { color: var(--qg-primary); font-size: 15px; }
}
.step-badge { margin-left: 6px; }
.step-list {
  padding: 8px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.step-item {
  border-radius: 8px;
  padding: 8px 12px;
  background: var(--qg-bg-card-soft);
  border-left: 3px solid transparent;

  &.step-routing { border-left-color: #3b82f6; }
  &.step-planning { border-left-color: #6366f1; background: transparent; padding: 4px 12px; }
  &.step-thinking { border-left-color: #a78bfa; }
  &.step-thought { border-left-color: #8b5cf6; }
  &.step-tool_call { border-left-color: #f59e0b; }
  &.step-tool_result { border-left-color: #10b981; }
  &.step-error { border-left-color: #ef4444; }
}
.step-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
}
.step-icon { font-size: 14px; }
.step-label {
  font-weight: 600;
  color: var(--qg-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.tool-name {
  font-family: monospace;
  font-size: 11px;
  background: var(--qg-primary-weak);
  color: var(--qg-primary);
  padding: 1px 5px;
  border-radius: 4px;
}
.step-duration {
  font-size: 11px;
  color: var(--qg-text-secondary);
  margin-left: 4px;
}
.step-content { font-size: 13px; color: var(--qg-text-primary); line-height: 1.6; }
.thought-text { color: var(--qg-text-secondary); font-style: italic; }
.error-label { color: #ef4444; }
.error-text { color: #ef4444; }
.step-code {
  margin: 0;
  font-family: monospace;
  font-size: 11px;
  background: var(--qg-code-bg);
  color: var(--qg-code-text);
  padding: 8px;
  border-radius: 6px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}
.result-code { max-height: 160px; }
.cursor-blink {
  display: inline-block;
  animation: blink-cursor 0.8s step-end infinite;
  color: var(--qg-primary);
  font-weight: 300;
}
@keyframes blink-cursor {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.routing-tag { margin-left: 4px; }
.planning-label { color: #6366f1; font-style: italic; }
.step-thinking-dots {
  display: inline-flex;
  gap: 3px;
  margin-left: 8px;
  align-items: center;
  .dot {
    width: 4px; height: 4px; border-radius: 50%;
    background: #a78bfa;
    animation: blink 1.2s infinite both;
    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}
.stream-cursor {
  display: inline-block;
  color: #8b5cf6;
  animation: blink-cursor 0.7s step-end infinite;
  font-weight: 300;
  margin-left: 1px;
}
.empty-answer { color: var(--qg-text-secondary); font-style: italic; }

/* Fix2: 最终答案来源角标 */
.answer-sources {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--qg-border-subtle);
  font-size: 12px;
}
.sources-label { color: var(--qg-text-secondary); margin-right: 2px; }
.citation-link {
  color: var(--qg-primary);
  text-decoration: none;
  font-size: 11px;
  font-weight: 700;
  &:hover { text-decoration: underline; }
}
.source-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--qg-primary-weak);
  color: var(--qg-primary);
  text-decoration: none;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  &:hover { filter: brightness(0.85); }
}

/* I4-F2: web search 来源链接 */
.web-source-links {
  margin-top: 6px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 11px;
}
.source-links-label { color: var(--qg-text-secondary); margin-right: 2px; }
.source-link {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 10px;
  background: var(--qg-primary-weak);
  color: var(--qg-primary);
  text-decoration: none;
  font-size: 11px;
  font-weight: 600;
  &:hover { text-decoration: underline; filter: brightness(0.9); }
}

/* I4-F1: 追问气泡 */
.ask-user-bubble {
  border: 1.5px solid #f59e0b;
  border-radius: 12px;
  padding: 12px 16px;
  background: rgba(245, 158, 11, 0.06);
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.08);
}
.ask-user-question {
  font-size: 14px;
  color: var(--qg-text-primary);
  line-height: 1.6;
  margin-bottom: 10px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}
.ask-user-icon { flex-shrink: 0; }
.ask-user-reply-area {
  display: flex;
  gap: 8px;
  align-items: center;
  .ask-user-input { flex: 1; }
}
.ask-user-answered {
  font-size: 13px;
  color: var(--qg-text-secondary);
  padding: 4px 0;
}
.ask-user-answered-label {
  font-weight: 600;
  color: #f59e0b;
  margin-right: 4px;
}

/* 输入区 */
.chat-input-wrap {
  padding: 12px 18px 16px;
  border-top: 1px solid var(--qg-border-subtle);
  background: var(--qg-bg-card);
}
.composer {
  border-radius: var(--qg-radius-md);
  border: 1px solid var(--qg-border-subtle);
  overflow: hidden;
  background: var(--qg-bg-card-soft);
  box-shadow: 0 2px 8px rgba(0,0,0,0.03);
}
.composer-input {
  ::v-deep .el-textarea__inner {
    border: none;
    background: transparent;
    padding: 12px 16px;
    font-size: 14px;
    resize: none;
    color: var(--qg-text-primary);
    &:focus { box-shadow: none; }
  }
}
.input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px 8px;
  border-top: 1px solid var(--qg-border-subtle);
}
.actions-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.composer-select { width: 160px; }
.icon-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.send-btn {
  background: linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%) !important;
  border-color: transparent !important;
  &:hover { filter: brightness(1.08); }
}
</style>
