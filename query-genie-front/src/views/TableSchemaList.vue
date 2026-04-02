<template>
  <div class="qg-page schema-list-page">
    <div class="page-header">
      <div class="page-title">
        <i class="el-icon-table-lamp" />
        <span>表结构管理</span>
      </div>
      <div class="header-actions">
        <el-select v-model="selectedDatasourceId" placeholder="选择数据源" clearable style="width:200px;margin-right:12px" @change="loadList">
          <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
        </el-select>
        <el-button icon="el-icon-refresh" :disabled="!selectedDatasourceId" @click="showSyncDialog">一键同步</el-button>
        <el-button type="primary" icon="el-icon-plus" :disabled="!selectedDatasourceId" @click="goCreate">新增表</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="tableName" label="表名" width="180" />
        <el-table-column prop="alias" label="业务别名" width="160" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="字段数" width="80" align="center">
          <template #default="{ row }">
            {{ columnCount(row) }}
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90" align="center">
          <template #default="{ row }">
            <el-switch :value="row.enabled === 1" @change="val => toggleEnabled(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="text" icon="el-icon-edit" @click="goEdit(row)">编辑</el-button>
            <el-button type="text" icon="el-icon-refresh" @click="syncOne(row)">同步</el-button>
            <el-button type="text" icon="el-icon-delete" class="qg-danger-text" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 批量同步弹窗 -->
    <el-dialog title="同步表结构" :visible.sync="syncDialogVisible" width="460px">
      <el-form label-width="80px">
        <el-form-item label="表名">
          <el-input v-model="syncTableName" placeholder="输入要同步的表名" />
        </el-form-item>
      </el-form>
      <div style="color:var(--qg-text-secondary);font-size:13px;margin-top:4px">
        将从 information_schema 读取字段，已有别名和样本值不会被覆盖。
      </div>
      <template #footer>
        <el-button @click="syncDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="syncing" @click="handleSync">同步</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listDatasources } from '@/api/schema'
import { listTableSchemas, deleteTableSchema, syncTableSchema, updateTableSchema } from '@/api/schema'

export default {
  name: 'TableSchemaList',
  data() {
    return {
      datasources: [],
      selectedDatasourceId: null,
      list: [],
      loading: false,
      syncDialogVisible: false,
      syncTableName: '',
      syncing: false
    }
  },
  async created() {
    this.datasources = await listDatasources()
    if (this.datasources.length > 0) {
      this.selectedDatasourceId = this.datasources[0].id
      this.loadList()
    }
  },
  methods: {
    async loadList() {
      if (!this.selectedDatasourceId) { this.list = []; return }
      this.loading = true
      try {
        this.list = await listTableSchemas(this.selectedDatasourceId)
      } finally {
        this.loading = false
      }
    },
    columnCount(row) {
      try {
        return row.columnsJson ? JSON.parse(row.columnsJson).length : 0
      } catch (_) { return 0 }
    },
    goCreate() {
      this.$router.push({ path: '/schema/tables/edit', query: { datasourceId: this.selectedDatasourceId } })
    },
    goEdit(row) {
      this.$router.push({ path: '/schema/tables/edit', query: { id: row.id } })
    },
    showSyncDialog() {
      this.syncTableName = ''
      this.syncDialogVisible = true
    },
    async handleSync() {
      if (!this.syncTableName.trim()) {
        this.$message.warning('请输入表名')
        return
      }
      this.syncing = true
      try {
        await syncTableSchema(this.selectedDatasourceId, this.syncTableName.trim())
        this.$message.success('同步成功')
        this.syncDialogVisible = false
        this.loadList()
      } finally {
        this.syncing = false
      }
    },
    async syncOne(row) {
      const loading = this.$loading({ text: `同步 ${row.tableName} 中...` })
      try {
        await syncTableSchema(row.datasourceId, row.tableName)
        this.$message.success(`${row.tableName} 同步成功`)
        this.loadList()
      } finally {
        loading.close()
      }
    },
    async toggleEnabled(row, val) {
      await updateTableSchema(row.id, { ...row, enabled: val ? 1 : 0 })
      row.enabled = val ? 1 : 0
    },
    handleDelete(row) {
      this.$confirm(`确定删除表「${row.tableName}」的元数据？`, '提示', { type: 'warning' }).then(async () => {
        await deleteTableSchema(row.id)
        this.$message.success('删除成功')
        this.loadList()
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.schema-list-page { padding: 24px 0; }
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  .page-title {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 20px;
    font-weight: 600;
    color: var(--qg-text-primary);
    i { font-size: 22px; color: var(--qg-primary); }
  }
  .header-actions { display: flex; align-items: center; }
}
.table-card { border-radius: var(--qg-radius-md) !important; }
</style>
