import request from './request'

/**
 * 新建会话
 * @param {Object} params - knowledgeCodes 可选
 * @returns {Promise<{ sessionId: string, title: string }>}
 */
export function createSession(params = {}) {
  return request.post('/qa/sessions', params)
}

/**
 * 会话列表
 * @param {Object} params - offset, limit
 * @returns {Promise<Array<{ id, title, updateTime }>>}
 */
export function listSessions(params = {}) {
  return request.get('/qa/sessions', { params })
}

/**
 * 会话详情（含消息列表）
 * @param {string} id - 会话 ID
 * @returns {Promise<{ id, title, knowledgeCodes, createTime, updateTime, messages: Array }>}
 */
export function getSession(id) {
  return request.get(`/qa/sessions/${id}`)
}

/**
 * 删除会话
 * @param {string} id - 会话 ID
 */
export function deleteSession(id) {
  return request.delete(`/qa/sessions/${id}`)
}
