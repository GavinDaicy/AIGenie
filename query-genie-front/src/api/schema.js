import request from './request'

// =============================================
// 数据源管理
// =============================================

export function listDatasources() {
  return request.get('/schema/datasources')
}

export function createDatasource(data) {
  return request.post('/schema/datasources', data)
}

export function updateDatasource(id, data) {
  return request.put(`/schema/datasources/${id}`, data)
}

export function deleteDatasource(id) {
  return request.delete(`/schema/datasources/${id}`)
}

export function testConnection(id) {
  return request.post(`/schema/datasources/${id}/test`)
}

// =============================================
// 表结构元数据管理
// =============================================

export function listTableSchemas(datasourceId) {
  return request.get('/schema/tables', { params: { datasourceId } })
}

export function getTableSchema(id) {
  return request.get(`/schema/tables/${id}`)
}

export function createTableSchema(data) {
  return request.post('/schema/tables', data)
}

export function updateTableSchema(id, data) {
  return request.put(`/schema/tables/${id}`, data)
}

export function deleteTableSchema(id) {
  return request.delete(`/schema/tables/${id}`)
}

export function syncTableSchema(datasourceId, tableName) {
  return request.post('/schema/tables/sync', null, { params: { datasourceId, tableName } })
}

export function updateTableColumns(id, columns) {
  return request.put(`/schema/tables/${id}/columns`, columns)
}
