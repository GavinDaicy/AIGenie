import request from './request'

/**
 * Agent 问答接口（SSE流式）— 暂留空实现，迭代3实现。
 * @param {Object} params - sessionId, question, knowledgeCodes, datasourceIds
 * @param {Object} options - onStep(stepEvent), onChunk(text), onDone()
 * @returns {Function} abort - 调用可关闭 SSE 连接
 */
export function agentAskStream(params, options = {}) {
  const { onStep, onChunk, onDone, onError } = options
  const url = '/genie/api/agent/ask/stream'
  const controller = new AbortController()

  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
    signal: controller.signal
  }).then(response => {
    if (!response.ok) {
      return response.json()
        .then(d => { if (onError) onError(new Error(d.message || '请求失败')) })
        .catch(() => { if (onError) onError(new Error('请求失败')) })
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    function processLine(line) {
      if (!line.startsWith('data:')) return
      const raw = line.slice(5).trim()
      if (raw === '[DONE]') {
        if (onDone) onDone()
        return
      }
      try {
        const event = JSON.parse(raw)
        if (event.type === 'FINAL_ANSWER' && onChunk) {
          onChunk(event.content || '')
        } else if (onStep) {
          onStep(event)
        }
      } catch (_) {}
    }

    function read() {
      reader.read().then(({ value, done }) => {
        if (done) { if (onDone) onDone(); return }
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split(/\r?\n/)
        buffer = lines.pop() || ''
        lines.forEach(processLine)
        read()
      }).catch(err => { if (onError) onError(err) })
    }
    read()
  }).catch(err => {
    if (err.name !== 'AbortError' && onError) onError(err)
  })

  return () => controller.abort()
}

/**
 * 提交 Agent 回答评分反馈
 */
export function submitFeedback(data) {
  return request.post('/agent/feedback', data)
}
