import request from './request'

export function getDocumentList(knowledgeCode) {
  return request.get('/doc/list', { params: knowledgeCode ? { knowledgeCode } : {} })
}

export function uploadDocument(formData) {
  return request.post('/doc/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function addRemoteDocument(payload) {
  return request.post('/doc/addRemote', payload)
}

export function updateDocument(payload) {
  return request.put('/doc/update', payload)
}

export function deleteDocument(documentId) {
  return request.delete(`/doc/delete/${documentId}`)
}

export function getChunksByDocumentId(documentId) {
  return request.get(`/doc/${documentId}/chunks`)
}
