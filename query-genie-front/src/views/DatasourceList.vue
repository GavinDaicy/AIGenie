<template>
  <div class="qg-page datasource-page">
    <div class="page-header">
      <div class="page-title">
        <i class="el-icon-coin" />
        <span>数据源管理</span>
      </div>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新增数据源</el-button>
    </div>

    <el-card shadow="never" class="table-card">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="dbUrl" label="JDBC URL" min-width="240" show-overflow-tooltip />
        <el-table-column prop="dbUsername" label="用户名" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="text" icon="el-icon-connection" @click="handleTest(row)">测试</el-button>
            <el-button type="text" icon="el-icon-edit" @click="openEdit(row)">编辑</el-button>
            <el-button type="text" icon="el-icon-delete" class="qg-danger-text" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="form.id ? '编辑数据源' : '新增数据源'" :visible.sync="dialogVisible" width="560px" @close="resetForm">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：生产MySQL" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" placeholder="简短说明该数据源用途" />
        </el-form-item>
        <el-form-item label="JDBC URL" prop="dbUrl">
          <el-input v-model="form.dbUrl" placeholder="jdbc:mysql://host:3306/dbname?..." />
        </el-form-item>
        <el-form-item label="用户名" prop="dbUsername">
          <el-input v-model="form.dbUsername" placeholder="数据库用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="dbPassword">
          <el-input v-model="form.dbPassword" type="password" show-password
            :placeholder="form.id ? '不修改请留空' : '数据库密码'" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listDatasources, createDatasource, updateDatasource, deleteDatasource, testConnection } from '@/api/schema'

export default {
  name: 'DatasourceList',
  data() {
    return {
      list: [],
      loading: false,
      saving: false,
      dialogVisible: false,
      form: { id: null, name: '', description: '', dbUrl: '', dbUsername: '', dbPassword: '', status: 1 },
      rules: {
        name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
        dbUrl: [{ required: true, message: '请输入 JDBC URL', trigger: 'blur' }],
        dbUsername: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        dbPassword: [
          {
            validator: (rule, value, cb) => {
              if (!this.form.id && !value) cb(new Error('请输入密码'))
              else cb()
            },
            trigger: 'blur'
          }
        ]
      }
    }
  },
  created() {
    this.loadList()
  },
  methods: {
    async loadList() {
      this.loading = true
      try {
        this.list = await listDatasources()
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      this.resetForm()
      this.dialogVisible = true
    },
    openEdit(row) {
      this.form = { id: row.id, name: row.name, description: row.description, dbUrl: row.dbUrl, dbUsername: row.dbUsername, dbPassword: '', status: row.status }
      this.dialogVisible = true
    },
    resetForm() {
      this.form = { id: null, name: '', description: '', dbUrl: '', dbUsername: '', dbPassword: '', status: 1 }
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    handleSave() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.saving = true
        try {
          if (this.form.id) {
            await updateDatasource(this.form.id, this.form)
            this.$message.success('更新成功')
          } else {
            await createDatasource(this.form)
            this.$message.success('创建成功')
          }
          this.dialogVisible = false
          this.loadList()
        } finally {
          this.saving = false
        }
      })
    },
    async handleTest(row) {
      const loading = this.$loading({ text: '连接测试中...' })
      try {
        const res = await testConnection(row.id)
        if (res && res.success) {
          this.$message.success('连接成功')
        } else {
          this.$message.error((res && res.message) || '连接失败')
        }
      } finally {
        loading.close()
      }
    },
    handleDelete(row) {
      this.$confirm(`确定删除数据源「${row.name}」？`, '提示', { type: 'warning' }).then(async () => {
        await deleteDatasource(row.id)
        this.$message.success('删除成功')
        this.loadList()
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.datasource-page {
  padding: 24px 0;
}
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
}
.table-card {
  border-radius: var(--qg-radius-md) !important;
}
</style>
