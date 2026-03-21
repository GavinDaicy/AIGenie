<template>
  <div class="knowledge-detail-page qg-page">
    <div class="page-header">
      <el-button icon="el-icon-arrow-left" @click="$router.push('/knowledge')">
        返回列表
      </el-button>
      <h1 class="detail-title">{{ knowledge.name || knowledgeCode }}</h1>
    </div>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="文档列表" name="doc">
        <el-card class="detail-card" shadow="never">
          <div slot="header" class="card-header">
            <span>文档列表</span>
            <div>
              <el-button type="primary" size="small" icon="el-icon-upload2" @click="openUpload">
                上传文档
              </el-button>
              <el-button size="small" icon="el-icon-link" @click="openRemote">添加远程文档</el-button>
            </div>
          </div>
          <el-table :data="docList" stripe v-loading="docLoading">
            <el-table-column prop="name" label="文档名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template slot-scope="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170">
              <template slot-scope="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template slot-scope="{ row }">
                <el-button type="text" size="small" @click="openChunks(row)">分块</el-button>
                <el-button type="text" size="small" @click="openEditDoc(row)">编辑</el-button>
                <el-button type="text" size="small" class="danger-btn" @click="handleDeleteDoc(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 上传文档 -->
    <el-dialog title="上传文档" :visible.sync="uploadVisible" width="500px" @close="resetUpload">
      <el-form ref="uploadRef" :model="uploadForm" label-width="100px">
        <el-form-item label="文档名称">
          <el-input v-model="uploadForm.name" placeholder="留空则使用文件名" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadEl"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-button size="small" type="primary">选择文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="submitUpload">上传</el-button>
      </span>
    </el-dialog>

    <!-- 远程文档 -->
    <el-dialog title="添加远程文档" :visible.sync="remoteVisible" width="500px">
      <el-form ref="remoteRef" :model="remoteForm" :rules="remoteRules" label-width="100px">
        <el-form-item label="类型" prop="docType">
          <el-select v-model="remoteForm.docType" placeholder="请选择" style="width: 100%">
            <el-option label="网页链接" value="web" />
            <el-option label="语雀链接" value="yuque" />
          </el-select>
        </el-form-item>
        <el-form-item label="链接" prop="url">
          <el-input v-model="remoteForm.url" placeholder="语雀或网页链接" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="remoteForm.name" placeholder="文档名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="remoteForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="remoteVisible = false">取消</el-button>
        <el-button type="primary" :loading="remoteLoading" @click="submitRemote">添加</el-button>
      </span>
    </el-dialog>

    <!-- 编辑文档 -->
    <el-dialog title="编辑文档" :visible.sync="editDocVisible" width="500px">
      <el-form ref="editDocRef" :model="editDocForm" :rules="editDocRules" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editDocForm.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editDocForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="editDocVisible = false">取消</el-button>
        <el-button type="primary" :loading="editDocLoading" @click="submitEditDoc">保存</el-button>
      </span>
    </el-dialog>

    <!-- 分块列表 -->
    <el-dialog title="文档分块" :visible.sync="chunksVisible" width="700px">
      <p v-if="currentDoc" class="doc-name">文档：{{ currentDoc.name }}</p>
      <el-table :data="chunksList" stripe v-loading="chunksLoading" max-height="400">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="内容" min-width="300">
          <template slot-scope="{ row }">
            <div class="chunk-preview">{{ formatChunkRow(row) }}</div>
          </template>
        </el-table-column>
      </el-table>
      <span slot="footer">
        <el-button @click="chunksVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getKnowledgeList } from '@/api/knowledge'
import {
  getDocumentList,
  uploadDocument,
  addRemoteDocument,
  updateDocument,
  deleteDocument,
  getChunksByDocumentId
} from '@/api/document'

const STATUS_MAP = {
  uploading: { text: '上传中', type: 'info' },
  toParse: { text: '待解析', type: 'warning' },
  parsing: { text: '解析中', type: 'primary' },
  parsed: { text: '解析完成', type: 'success' },
  failed: { text: '解析失败', type: 'danger' }
}

export default {
  name: 'KnowledgeDetail',
  data() {
    return {
      knowledge: {},
      activeTab: 'doc',
      docList: [],
      docLoading: false,
      uploadVisible: false,
      uploadForm: { name: '', description: '' },
      uploadFile: null,
      uploadLoading: false,
      remoteVisible: false,
      remoteForm: { docType: 'web', url: '', name: '', description: '' },
      remoteRules: {
        docType: [{ required: true, message: '请选择类型', trigger: 'change' }],
        url: [{ required: true, message: '请输入链接', trigger: 'blur' }],
        name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
      },
      remoteLoading: false,
      editDocVisible: false,
      editDocForm: { id: '', name: '', description: '' },
      editDocRules: { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] },
      editDocLoading: false,
      chunksVisible: false,
      chunksList: [],
      chunksLoading: false,
      currentDoc: null
    }
  },
  computed: {
    knowledgeCode() {
      return this.$route.params.code || ''
    }
  },
  watch: {
    knowledgeCode: {
      immediate: true,
      handler(code) {
        if (code) {
          this.loadKnowledge()
          this.loadDocList()
        }
      }
    }
  },
  methods: {
    async loadKnowledge() {
      try {
        const list = await getKnowledgeList() || []
        this.knowledge = list.find(k => k.code === this.knowledgeCode) || {}
      } catch (e) {
        this.knowledge = {}
      }
    },
    async loadDocList() {
      this.docLoading = true
      try {
        this.docList = await getDocumentList(this.knowledgeCode) || []
      } finally {
        this.docLoading = false
      }
    },
    statusText(s) {
      return (STATUS_MAP[s] || {}).text || s
    },
    statusTagType(s) {
      return (STATUS_MAP[s] || {}).type || 'info'
    },
    formatTime(t) {
      if (!t) return '-'
      const d = new Date(t)
      return d.toLocaleString('zh-CN')
    },
    openUpload() {
      this.uploadVisible = true
    },
    onFileChange(file) {
      this.uploadFile = file.raw
      if (!this.uploadForm.name) this.uploadForm.name = file.name
    },
    onFileRemove() {
      this.uploadFile = null
    },
    resetUpload() {
      this.uploadForm = { name: '', description: '' }
      this.uploadFile = null
      this.$refs.uploadEl && this.$refs.uploadEl.clearFiles()
    },
    async submitUpload() {
      if (!this.uploadFile) {
        this.$message.warning('请选择文件')
        return
      }
      this.uploadLoading = true
      try {
        const formData = new FormData()
        formData.append('file', this.uploadFile)
        formData.append('knowledgeCode', this.knowledgeCode)
        if (this.uploadForm.name) formData.append('name', this.uploadForm.name)
        if (this.uploadForm.description) formData.append('description', this.uploadForm.description)
        await uploadDocument(formData)
        this.$message.success('上传成功，文档将自动解析与向量化')
        this.uploadVisible = false
        this.loadDocList()
      } finally {
        this.uploadLoading = false
      }
    },
    openRemote() {
      this.remoteForm = { docType: 'web', url: '', name: '', description: '' }
      this.remoteVisible = true
    },
    async submitRemote() {
      try {
        await this.$refs.remoteRef.validate()
      } catch (e) { return }
      this.remoteLoading = true
      try {
        await addRemoteDocument({
          knowledgeCode: this.knowledgeCode,
          docType: this.remoteForm.docType,
          url: this.remoteForm.url,
          name: this.remoteForm.name,
          description: this.remoteForm.description,
          category: 'UNSTRUCTURED'
        })
        this.$message.success('添加成功，将自动拉取并解析')
        this.remoteVisible = false
        this.loadDocList()
      } finally {
        this.remoteLoading = false
      }
    },
    openEditDoc(row) {
      this.editDocForm = { id: row.id, name: row.name, description: row.description || '' }
      this.editDocVisible = true
    },
    async submitEditDoc() {
      try {
        await this.$refs.editDocRef.validate()
      } catch (e) { return }
      this.editDocLoading = true
      try {
        await updateDocument(this.editDocForm)
        this.$message.success('保存成功')
        this.editDocVisible = false
        this.loadDocList()
      } finally {
        this.editDocLoading = false
      }
    },
    handleDeleteDoc(row) {
      this.$confirm(`确定删除文档「${row.name}」吗？`, '提示', { type: 'warning' })
        .then(async () => {
          await deleteDocument(row.id)
          this.$message.success('已删除')
          this.loadDocList()
        })
        .catch(() => {})
    },
    openChunks(row) {
      this.currentDoc = row
      this.chunksVisible = true
      this.chunksList = []
      this.chunksLoading = true
      getChunksByDocumentId(row.id)
        .then(list => { this.chunksList = list || [] })
        .finally(() => { this.chunksLoading = false })
    },
    formatChunkRow(row) {
      if (typeof row === 'string') return row
      if (row.content) return row.content
      const parts = []
      for (const k of Object.keys(row)) {
        if (k !== 'vector' && row[k] != null && row[k] !== '') parts.push(String(row[k]))
      }
      return parts.join(' ') || '-'
    }
  }
}
</script>

<style lang="scss" scoped>
.knowledge-detail-page {
  padding-bottom: 32px;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 24px;
  .el-button {
    flex-shrink: 0;
  }
}
.detail-title {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--qg-text-primary);
}
.detail-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 18px;
  }
}
.detail-card {
  border-radius: var(--qg-radius-lg) !important;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.doc-name {
  margin-bottom: 12px;
  color: var(--qg-text-secondary);
}
.chunk-preview {
  max-height: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  line-height: 1.5;
  color: var(--qg-text-primary);
}
.danger-btn {
  color: #f56c6c !important;
}
</style>
