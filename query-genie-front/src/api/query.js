import request from './request'

/**
 * @param {Object} params - query, knowledgeCodes?, mode?: 'KEYWORD'|'VECTOR'|'HYBRID', size?, normalizeScore?: boolean, rerank?: boolean 是否对结果做重排序（需后端开启 app.rerank.enabled）
 */
export function search(params) {
  return request.post('/query/search', params)
}

export function searchGet(params) {
  return request.get('/query/search', { params })
}
