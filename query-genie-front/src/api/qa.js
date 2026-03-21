import request from './request'


/**
 * 智能问答（一次性返回）：调用 POST /qa/ask
 * @param {Object} params - question（必填）, sessionId?, knowledgeCodes?, mode?, size?, rerank?
 * @returns {Promise<{ answer: string, sources: Array, sessionId?: string, messageId?: string }>}
 */
export function ask(params) {
  return request.post('/qa/ask', params)
}

/**
 * 流式智能问答：调用 POST /qa/ask/stream，通过 SSE 按块推送答案
 * @param {Object} params - question（必填）, sessionId?, knowledgeCodes?, size?, rerank?
 * @param {{ onChunk: (text: string) => void, onRewritten: (queries: string[]) => void }} options - onChunk 每收到一段正文调用；onRewritten 收到改写问句时调用（可选）
 * @returns {Promise<{ sources: Array, rewrittenQueries?: string[], sessionId?: string, messageId?: string }>}
 */
export function askStream(params, options = {}) {
  const { onChunk, onRewritten } = options
  const url = '/genie/api/qa/ask/stream'
  return new Promise((resolve, reject) => {
    fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params)
    }).then(response => {
      if (!response.ok) {
        return response.json().then(d => reject(new Error(d.message || '请求失败'))).catch(() => reject(new Error('请求失败')))
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''
      let result = { sources: [], rewrittenQueries: null, sessionId: null, messageId: null }

      function processLine(line) {
        if (line.startsWith('event:')) {
          currentEvent = line.slice(6).trim()
          return
        }
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (currentEvent === 'rewritten' && data) {
            try {
              const list = JSON.parse(data)
              result.rewrittenQueries = Array.isArray(list) ? list : []
              if (onRewritten && result.rewrittenQueries.length) onRewritten(result.rewrittenQueries)
            } catch (_) {}
          }
          if (currentEvent === 'chunk' && data && onChunk) {
          try {
            const text = (data.startsWith('"') ? JSON.parse(data) : data)
            onChunk(typeof text === 'string' ? text : data)
          } catch (_) {
            onChunk(data)
          }
        }
          if (currentEvent === 'sources') {
            try {
              result.sources = JSON.parse(data) || []
            } catch (_) {}
          }
          if (currentEvent === 'done') {
            try {
              const done = JSON.parse(data) || {}
              result.sessionId = done.sessionId || null
              result.messageId = done.messageId || null
            } catch (_) {}
            resolve(result)
          }
          if (currentEvent === 'error') {
            reject(new Error(data || '流式输出错误'))
          }
          currentEvent = ''
          return
        }
      }

      function read() {
        reader.read().then(({ value, done }) => {
          if (done) {
            if (!result.messageId && !result.sessionId && result.sources.length === 0) resolve(result)
            return
          }
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split(/\r?\n/)
          buffer = lines.pop() || ''
          lines.forEach(processLine)
          read()
        }).catch(reject)
      }
      read()
    }).catch(reject)
  })
}
