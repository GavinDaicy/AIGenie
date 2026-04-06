<template>
  <div class="qg-page schema-edit-page">
    <div class="page-header">
      <el-button type="text" icon="el-icon-arrow-left" @click="$router.back()">返回</el-button>
      <div class="page-title">
        <i class="el-icon-document" />
        <span>{{ isEdit ? '编辑表结构' : '新增表结构' }}</span>
      </div>
    </div>

    <el-card shadow="never" class="edit-card" v-loading="pageLoading">
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <!-- 基础信息 -->
        <div class="section-title">基础信息</div>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="数据源" prop="datasourceId">
              <el-select v-model="form.datasourceId" placeholder="请选择数据源" style="width:100%" :disabled="isEdit">
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="表名" prop="tableName">
              <el-input v-model="form.tableName" placeholder="实际数据库表名" :disabled="isEdit" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="业务别名">
              <el-input v-model="form.alias" placeholder="如：钢筋价格表" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="表的业务说明（会注入到 LLM Prompt）" />
        </el-form-item>

        <!-- 字段元数据 -->
        <div class="section-title">
          字段元数据
          <el-button type="text" icon="el-icon-plus" @click="addColumn">添加字段</el-button>
        </div>
        <el-table :data="form.columns" border size="small" class="col-table">
          <el-table-column label="字段名" width="140">
            <template #default="{ row }">
              <el-input v-model="row.name" size="mini" placeholder="字段名" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-input v-model="row.type" size="mini" placeholder="如 varchar" />
            </template>
          </el-table-column>
          <el-table-column label="业务别名" width="130">
            <template #default="{ row }">
              <el-input v-model="row.alias" size="mini" placeholder="别名" />
            </template>
          </el-table-column>
          <el-table-column label="描述">
            <template #default="{ row }">
              <el-input v-model="row.description" size="mini" placeholder="字段业务说明" />
            </template>
          </el-table-column>
          <el-table-column label="样本值（逗号分隔）" width="200">
            <template #default="{ row }">
              <el-input v-model="row.sampleValuesStr" size="mini" placeholder="HRB400,HRB500" @change="parseSampleValues(row)" />
            </template>
          </el-table-column>
          <el-table-column label="" width="50" align="center">
            <template #default="{ $index }">
              <el-button type="text" icon="el-icon-delete" class="qg-danger-text" @click="removeColumn($index)" />
            </template>
          </el-table-column>
        </el-table>

        <!-- 示例问答对 -->
        <div class="section-title" style="margin-top:24px">
          示例问答（Few-shot）
          <el-button type="text" icon="el-icon-plus" @click="addQuery">添加示例</el-button>
        </div>
        <div v-for="(q, i) in form.sampleQueries" :key="i" class="query-item">
          <div class="query-index">{{ i + 1 }}</div>
          <div class="query-fields">
            <el-input v-model="q.question" placeholder="自然语言问题" style="margin-bottom:6px" />
            <el-input v-model="q.sql" type="textarea" :rows="2" placeholder="对应 SQL 语句" />
          </div>
          <el-button type="text" icon="el-icon-delete" class="qg-danger-text query-del" @click="removeQuery(i)" />
        </div>
        <div v-if="form.sampleQueries.length === 0" class="empty-tip">暂无示例，点击"添加示例"</div>
      </el-form>
    </el-card>

    <div class="page-footer">
      <el-button @click="$router.back()">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </div>
  </div>
</template>

<script>
import { listDatasources, getTableSchema, createTableSchema, updateTableSchema } from '@/api/schema'

export default {
  name: 'TableSchemaEdit',
  data() {
    return {
      pageLoading: false,
      saving: false,
      datasources: [],
      form: {
        id: null,
        datasourceId: null,
        tableName: '',
        alias: '',
        description: '',
        columns: [],
        sampleQueries: [],
        enabled: 1
      },
      rules: {
        datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
        tableName: [{ required: true, message: '请输入表名', trigger: 'blur' }]
      }
    }
  },
  computed: {
    isEdit() {
      return !!this.$route.query.id
    }
  },
  async created() {
    this.datasources = await listDatasources()
    if (this.isEdit) {
      await this.loadSchema()
    } else if (this.$route.query.datasourceId) {
      this.form.datasourceId = Number(this.$route.query.datasourceId)
    }
  },
  methods: {
    async loadSchema() {
      this.pageLoading = true
      try {
        const schema = await getTableSchema(this.$route.query.id)
        const columns = schema.columnsJson ? JSON.parse(schema.columnsJson) : []
        columns.forEach(c => { c.sampleValuesStr = c.sampleValues ? c.sampleValues.join(',') : '' })
        const sampleQueries = schema.sampleQueries ? JSON.parse(schema.sampleQueries) : []
        this.form = {
          id: schema.id,
          datasourceId: schema.datasourceId,
          tableName: schema.tableName,
          alias: schema.alias || '',
          description: schema.description || '',
          columns,
          sampleQueries,
          enabled: schema.enabled
        }
      } finally {
        this.pageLoading = false
      }
    },
    addColumn() {
      this.form.columns.push({ name: '', type: '', alias: '', description: '', sampleValues: [], sampleValuesStr: '' })
    },
    removeColumn(index) {
      this.form.columns.splice(index, 1)
    },
    parseSampleValues(row) {
      row.sampleValues = row.sampleValuesStr
        ? row.sampleValuesStr.split(',').map(s => s.trim()).filter(Boolean)
        : []
    },
    addQuery() {
      this.form.sampleQueries.push({ question: '', sql: '' })
    },
    removeQuery(index) {
      this.form.sampleQueries.splice(index, 1)
    },
    handleSave() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.saving = true
        const payload = {
          datasourceId: this.form.datasourceId,
          tableName: this.form.tableName,
          alias: this.form.alias,
          description: this.form.description,
          columns: this.form.columns.map(({ sampleValuesStr, ...rest }) => rest),
          sampleQueries: this.form.sampleQueries,
          enabled: this.form.enabled
        }
        try {
          if (this.isEdit) {
            await updateTableSchema(this.form.id, payload)
            this.$message.success('保存成功')
          } else {
            await createTableSchema(payload)
            this.$message.success('创建成功')
          }
          this.$router.back()
        } finally {
          this.saving = false
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.schema-edit-page { padding: 24px 0; }
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  .page-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 20px;
    font-weight: 600;
    color: var(--qg-text-primary);
    i { color: var(--qg-primary); font-size: 22px; }
  }
}
.edit-card { border-radius: var(--qg-radius-md) !important; padding: 8px; }
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--qg-text-secondary);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin: 20px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--qg-border-subtle);
  display: flex;
  align-items: center;
  gap: 12px;
  &:first-of-type { margin-top: 4px; }
}
.col-table { margin-top: 4px; }
.query-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--qg-border-subtle);
  border-radius: var(--qg-radius-md);
  background: var(--qg-bg-card-soft);
  .query-index {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--qg-primary);
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 4px;
  }
  .query-fields { flex: 1; }
  .query-del { margin-top: 4px; flex-shrink: 0; }
}
.empty-tip {
  color: var(--qg-text-secondary);
  font-size: 13px;
  text-align: center;
  padding: 20px;
  border: 1px dashed var(--qg-border-subtle);
  border-radius: var(--qg-radius-md);
}
.page-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
</style>
