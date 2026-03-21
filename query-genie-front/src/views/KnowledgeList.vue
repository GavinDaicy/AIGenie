<template>
  <div class="knowledge-list-page qg-page">
    <div class="page-hero">
      <div class="page-hero-text">
        <h1 class="page-hero-title">知识库</h1>
        <p class="page-hero-desc">创建、发布与维护知识库及字段配置</p>
      </div>
      <el-button type="primary" icon="el-icon-plus" @click="openCreate">新建知识库</el-button>
    </div>
    <el-card class="qg-table-card" shadow="never">
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template slot-scope="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">未发布</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">发布后修改中</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">已发布</el-tag>
            <span v-else>{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="text" size="small" @click="goDetail(row.code)">文档管理</el-button>
            <el-button type="text" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.status !== 2"
              type="text"
              size="small"
              @click="handlePublish(row.code)"
            >
              发布
            </el-button>
            <el-button type="text" size="small" class="danger-btn" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="900px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="编码" prop="code">
          <el-input v-model="form.code" placeholder="英文/数字，唯一" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
        <el-form-item label="分块策略">
          <el-select v-model="form.chunkingStrategy" placeholder="请选择分块策略" style="width: 260px">
            <el-option label="长文本（按段落/长度）" value="RECURSIVE_TEXT" />
            <el-option label="Markdown 按标题/章节" value="MARKDOWN_HEADING" />
            <el-option label="表格每行一块" value="ROW_BASED" />
          </el-select>
          <span class="form-tip">决定文档解析后的分块方式，影响召回粒度与上下文效果。</span>
        </el-form-item>
        <el-form-item label="主内容字段">
          <el-select
            v-model="form.primaryContentFieldKey"
            placeholder="可选，建议选择长文本字段"
            :disabled="!form.fields || !form.fields.length"
            style="width: 260px"
          >
            <el-option
              v-for="f in form.fields"
              :key="f.fieldKey"
              :label="f.name || f.fieldKey"
              :value="f.fieldKey"
            />
          </el-select>
          <span class="form-tip">用于长文本类知识库标记主要内容字段，便于分块与召回。</span>
        </el-form-item>
        <el-form-item label="自定义字段">
          <div class="field-list-wrap">
            <p v-if="!form.id" class="field-warn-tip"><i class="el-icon-warning-outline" /> 自定义字段创建后不允许更改，请谨慎配置。</p>
            <el-button v-if="!form.id" type="text" icon="el-icon-plus" @click="addField">添加字段</el-button>
            <el-table
              :data="form.fields"
              border
              size="small"
              class="field-table"
              :empty-text="form.id ? '暂无字段信息' : '暂无自定义字段'"
            >
              <el-table-column label="字段标识" min-width="120">
                <template slot-scope="{ row }">
                  <el-input v-if="!form.id" v-model="row.fieldKey" placeholder="英文/数字" size="small" />
                  <span v-else>
                    {{ row.fieldKey }}
                    <el-tag v-if="isBuiltInField(row.fieldKey)" type="info" size="mini" class="built-in-tag">内置</el-tag>
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="字段名称" min-width="100">
                <template slot-scope="{ row }">
                  <el-input v-if="!form.id" v-model="row.name" placeholder="显示名称" size="small" />
                  <span v-else>{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column label="描述" min-width="100" show-overflow-tooltip>
                <template slot-scope="{ row }">
                  <el-input v-if="!form.id" v-model="row.description" placeholder="选填" size="small" />
                  <span v-else>{{ row.description || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="110">
                <template slot-scope="{ row }">
                  <el-select v-if="!form.id" v-model="row.type" placeholder="类型" size="small" style="width: 100%">
                    <el-option label="字符串" value="string" />
                    <el-option label="整数" value="integer" />
                    <el-option label="浮点" value="float" />
                    <el-option label="双精度" value="double" />
                    <el-option label="日期" value="date" />
                  </el-select>
                  <span v-else>{{ formatFieldType(row.type) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="精准匹配" width="80" align="center">
                <template slot-scope="{ row }">
                  <el-switch v-if="!form.id" v-model="row.matchable" />
                  <span v-else>{{ row.matchable ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="全文检索" width="80" align="center">
                <template slot-scope="{ row }">
                  <el-switch v-if="!form.id" v-model="row.fullTextSearchable" />
                  <span v-else>{{ row.fullTextSearchable ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="语义检索" width="80" align="center">
                <template slot-scope="{ row }">
                  <el-switch v-if="!form.id" v-model="row.semanticSearchable" />
                  <span v-else>{{ row.semanticSearchable ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="可排序" width="75" align="center">
                <template slot-scope="{ row }">
                  <el-switch v-if="!form.id" v-model="row.sortable" />
                  <span v-else>{{ row.sortable ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="权重" width="90" align="center">
                <template slot-scope="{ row }">
                  <el-input-number
                    v-if="!form.id"
                    v-model="row.boost"
                    :min="0.1"
                    :max="10"
                    :step="0.1"
                    size="small"
                  />
                  <span v-else>{{ row.boost != null ? row.boost : 1 }}</span>
                </template>
              </el-table-column>
              <el-table-column v-if="!form.id" label="操作" width="60" align="center" fixed="right">
                <template slot-scope="{ $index }">
                  <el-button type="text" size="small" class="danger-btn" @click="removeField($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-if="form.fields && form.fields.length === 0" class="field-empty-tip">
              {{ form.id ? '该知识库暂无自定义字段' : '可点击「添加字段」增加（系统会自动包含 id、doc_id、sys_content 固定字段）' }}
            </p>
          </div>
        </el-form-item>

        <el-form-item label="时间衰减">
          <el-switch v-model="form.timeDecay.enabled" />
          <span class="form-tip">将指定日期字段作为新鲜度索引，对检索/问答结果进行时间加权排序。</span>
        </el-form-item>
        <template v-if="form.timeDecay && form.timeDecay.enabled">
          <el-form-item label="时间来源">
            <el-radio-group v-model="form.timeDecay.fieldSource">
              <el-radio label="BUSINESS_FIELD">业务日期字段</el-radio>
              <el-radio label="SYSTEM_DOC_UPDATE_TIME">文档更新时间</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.timeDecay.fieldSource === 'BUSINESS_FIELD'" label="日期字段">
            <el-select v-model="form.timeDecay.fieldKey" placeholder="请选择日期字段" style="width: 260px">
              <el-option
                v-for="f in dateFields"
                :key="f.fieldKey"
                :label="(f.name || f.fieldKey) + '（' + f.fieldKey + '）'"
                :value="f.fieldKey"
              />
            </el-select>
            <span class="form-tip">仅展示类型为“日期”的字段。</span>
          </el-form-item>
          <el-form-item label="衰减类型">
            <el-select v-model="form.timeDecay.decayType" placeholder="请选择" style="width: 260px">
              <el-option label="线性（LINEAR）" value="LINEAR" />
              <el-option label="指数（EXPONENTIAL）" value="EXPONENTIAL" />
              <el-option label="高斯（GAUSSIAN）" value="GAUSSIAN" />
            </el-select>
          </el-form-item>
          <el-form-item label="scale">
            <el-input v-model="form.timeDecay.scale" placeholder="如 30d / 7d / 12h" style="width: 260px" />
            <span class="form-tip">衰减尺度，越小越偏向新数据。</span>
          </el-form-item>
          <el-form-item label="offset">
            <el-input v-model="form.timeDecay.offset" placeholder="如 0d / 3d" style="width: 260px" />
            <span class="form-tip">在 offset 内不衰减。</span>
          </el-form-item>
          <el-form-item label="decay">
            <el-input-number v-model="form.timeDecay.decay" :min="0.01" :max="1" :step="0.01" style="width: 260px" />
            <span class="form-tip">用于推导指数/高斯参数，通常取 0.5。</span>
          </el-form-item>
          <el-form-item label="floor">
            <el-input-number v-model="form.timeDecay.floor" :min="0" :max="1" :step="0.05" style="width: 260px" />
            <span class="form-tip">时间权重下限，避免旧数据被完全压制。</span>
          </el-form-item>
          <el-form-item label="阈值">
            <el-input-number v-model="form.timeDecay.applyThreshold" :min="0" :max="10" :step="0.1" style="width: 260px" />
            <span class="form-tip">可选：仅当基础相关性分数达到阈值才应用时间衰减。</span>
          </el-form-item>
        </template>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getKnowledgeList, createKnowledge, updateKnowledge, publishKnowledge, deleteKnowledge } from '@/api/knowledge'

export default {
  name: 'KnowledgeList',
  data() {
    return {
      list: [],
      loading: false,
      dialogVisible: false,
      dialogTitle: '新建知识库',
      submitLoading: false,
      form: {
        id: '',
        code: '',
        name: '',
        description: '',
        chunkingStrategy: 'RECURSIVE_TEXT',
        primaryContentFieldKey: '',
        fields: [],
        timeDecay: {
          enabled: false,
          fieldSource: 'SYSTEM_DOC_UPDATE_TIME',
          fieldKey: '',
          decayType: 'EXPONENTIAL',
          scale: '30d',
          offset: '0d',
          decay: 0.5,
          floor: 0.2,
          applyThreshold: null,
          lambda: null,
          sigma: null
        }
      },
      rules: {
        code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
        name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
      }
    }
  },
  computed: {
    dateFields() {
      const fields = this.form && this.form.fields ? this.form.fields : []
      return (fields || []).filter(f => {
        const t = typeof f.type === 'string' ? f.type : (f.type && f.type.value)
        return t === 'date'
      })
    }
  },
  created() {
    this.loadList()
  },
  methods: {
    async loadList() {
      this.loading = true
      try {
        this.list = await getKnowledgeList() || []
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      this.dialogTitle = '新建知识库'
      this.form = {
        id: '',
        code: '',
        name: '',
        description: '',
        chunkingStrategy: 'RECURSIVE_TEXT',
        primaryContentFieldKey: '',
        fields: [],
        timeDecay: {
          enabled: false,
          fieldSource: 'SYSTEM_DOC_UPDATE_TIME',
          fieldKey: '',
          decayType: 'EXPONENTIAL',
          scale: '30d',
          offset: '0d',
          decay: 0.5,
          floor: 0.2,
          applyThreshold: null,
          lambda: null,
          sigma: null
        }
      }
      this.dialogVisible = true
    },
    openEdit(row) {
      this.dialogTitle = '编辑知识库'
      const fields = (row.fields || []).map(f => ({
        fieldKey: f.fieldKey,
        name: f.name,
        description: f.description || '',
        type: typeof f.type === 'string' ? f.type : (f.type && f.type.value) || 'string',
        matchable: !!f.matchable,
        fullTextSearchable: !!f.fullTextSearchable,
        semanticSearchable: !!f.semanticSearchable,
        sortable: !!f.sortable,
        boost: f.boost != null ? f.boost : 1
      }))
      const td = row.timeDecayConfig || {}
      this.form = {
        id: row.id,
        code: row.code,
        name: row.name,
        description: row.description || '',
        chunkingStrategy: (row.chunkingPolicy && row.chunkingPolicy.strategy) || 'RECURSIVE_TEXT',
        primaryContentFieldKey: row.primaryContentFieldKey || '',
        fields,
        timeDecay: {
          enabled: !!td.enabled,
          fieldSource: td.fieldSource || 'SYSTEM_DOC_UPDATE_TIME',
          fieldKey: td.fieldKey || '',
          decayType: td.decayType || 'EXPONENTIAL',
          scale: td.scale || '30d',
          offset: td.offset || '0d',
          decay: td.decay != null ? td.decay : 0.5,
          floor: td.floor != null ? td.floor : 0.2,
          applyThreshold: td.applyThreshold != null ? td.applyThreshold : null,
          lambda: td.lambda != null ? td.lambda : null,
          sigma: td.sigma != null ? td.sigma : null
        }
      }
      this.dialogVisible = true
    },
    formatFieldType(type) {
      const map = { string: '字符串', integer: '整数', float: '浮点', double: '双精度', date: '日期' }
      const t = typeof type === 'string' ? type : (type && type.value)
      return map[t] || t || '—'
    },
    isBuiltInField(fieldKey) {
      return fieldKey === 'id' || fieldKey === 'doc_id' || fieldKey === 'sys_content'
    },
    addField() {
      this.form.fields = this.form.fields || []
      this.form.fields.push({
        fieldKey: '',
        name: '',
        description: '',
        type: 'string',
        matchable: true,
        fullTextSearchable: true,
        semanticSearchable: true,
        sortable: false,
        boost: 1
      })
    },
    removeField(index) {
      this.form.fields.splice(index, 1)
    },
    submitForm() {
      this.$refs.formRef.validate(async valid => {
        if (!valid) return
        if (this.form.timeDecay && this.form.timeDecay.enabled) {
          if (this.form.timeDecay.fieldSource === 'BUSINESS_FIELD') {
            if (!this.form.timeDecay.fieldKey) {
              this.$message.warning('请选择时间衰减的日期字段')
              return
            }
          }
          if (!this.form.timeDecay.scale) {
            this.$message.warning('请填写时间衰减 scale（如 30d）')
            return
          }
        }
        if (!this.form.id) {
          const fields = this.form.fields || []
          const invalid = fields.some(f => !(f.fieldKey && f.name))
          if (invalid) {
            this.$message.warning('请填写每个自定义字段的「字段标识」和「字段名称」')
            return
          }
        }
        this.submitLoading = true
        try {
          if (this.form.id) {
            const payload = {
              id: this.form.id,
              code: this.form.code,
              name: this.form.name,
              description: this.form.description || '',
              chunkingPolicy: {
                strategy: this.form.chunkingStrategy
              },
              primaryContentFieldKey: this.form.primaryContentFieldKey || null,
              timeDecayConfig: this.buildTimeDecayPayload()
            }
            await updateKnowledge(payload)
            this.$message.success('更新成功')
          } else {
            const payload = {
              code: this.form.code,
              name: this.form.name,
              description: this.form.description || '',
              chunkingPolicy: {
                strategy: this.form.chunkingStrategy
              },
              primaryContentFieldKey: this.form.primaryContentFieldKey || null,
              timeDecayConfig: this.buildTimeDecayPayload(),
              fields: (this.form.fields || []).map((f, i) => ({
                fieldKey: f.fieldKey,
                name: f.name,
                description: f.description || '',
                type: f.type || 'string',
                matchable: f.matchable !== false,
                fullTextSearchable: f.fullTextSearchable !== false,
                semanticSearchable: f.semanticSearchable !== false,
                sortable: !!f.sortable,
                ord: i,
                boost: f.boost != null ? f.boost : 1
              }))
            }
            await createKnowledge(payload)
            this.$message.success('创建成功')
          }
          this.dialogVisible = false
          this.loadList()
        } finally {
          this.submitLoading = false
        }
      })
    },
    buildTimeDecayPayload() {
      const td = this.form.timeDecay || {}
      if (!td.enabled) return { enabled: false }
      return {
        enabled: true,
        fieldSource: td.fieldSource,
        fieldKey: td.fieldSource === 'BUSINESS_FIELD' ? (td.fieldKey || null) : null,
        decayType: td.decayType,
        scale: td.scale,
        offset: td.offset,
        decay: td.decay,
        floor: td.floor,
        applyThreshold: td.applyThreshold != null ? td.applyThreshold : null,
        lambda: td.lambda != null ? td.lambda : null,
        sigma: td.sigma != null ? td.sigma : null
      }
    },
    resetForm() {
      this.$refs.formRef && this.$refs.formRef.resetFields()
    },
    async handlePublish(code) {
      try {
        await publishKnowledge(code)
        this.$message.success('发布成功')
        this.loadList()
      } catch (e) {}
    },
    handleDelete(row) {
      this.$confirm(`确定删除知识库「${row.name}」吗？其下文档也会被清理。`, '提示', {
        type: 'warning'
      }).then(async () => {
        try {
          await deleteKnowledge(row.code)
          this.$message.success('已删除')
          this.loadList()
        } catch (e) {}
      }).catch(() => {})
    },
    goDetail(code) {
      this.$router.push({ path: `/knowledge/${code}` })
    }
  }
}
</script>

<style lang="scss" scoped>
.knowledge-list-page {
  padding-bottom: 32px;
}
.page-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}
.page-hero-text {
  min-width: 0;
}
.page-hero-title {
  margin: 0;
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -0.03em;
  color: var(--qg-text-primary);
}
.page-hero-desc {
  margin: 10px 0 0;
  font-size: 15px;
  line-height: 1.65;
  color: var(--qg-text-secondary);
  max-width: 520px;
}
.qg-table-card {
  border-radius: var(--qg-radius-lg) !important;
  :deep(.el-card__body) {
    padding: 8px 4px 16px;
  }
}
.field-list-wrap {
  width: 100%;
  .field-warn-tip {
    margin: 0 0 8px;
    color: #e6a23c;
    font-size: 13px;
    .el-icon-warning-outline { margin-right: 4px; }
  }
  .field-table { margin-top: 8px; }
  .built-in-tag { margin-left: 6px; vertical-align: middle; }
  .field-empty-tip { margin: 8px 0 0; color: #909399; font-size: 12px; }
}
.form-tip {
  margin-left: 10px;
  color: var(--qg-text-secondary);
  font-size: 12px;
}
.danger-btn {
  color: #f56c6c !important;
}
</style>
