import request from './request'

export function getKnowledgeList() {
  return request.get('/knowledge/list')
}

export function createKnowledge(payload) {
  return request.post('/knowledge/create', payload)
}

export function updateKnowledge(payload) {
  return request.put('/knowledge/updateBaseInfo', payload)
}

export function publishKnowledge(code) {
  return request.put(`/knowledge/publish/${code}`)
}

export function deleteKnowledge(code) {
  return request.delete(`/knowledge/delete/${code}`)
}
