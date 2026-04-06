import Vue from 'vue'
import VueRouter from 'vue-router'

Vue.use(VueRouter)

const routes = [
  { path: '/', redirect: '/search' },
  { path: '/search', name: 'Search', component: () => import('@/views/Search.vue'), meta: { title: '检索' } },
  { path: '/qa', name: 'Qa', component: () => import('@/views/Qa.vue'), meta: { title: '智能问答' } },
  { path: '/agent', name: 'Agent', component: () => import('@/views/AgentChat.vue'), meta: { title: 'Agent 助手' } },
  { path: '/knowledge', name: 'Knowledge', component: () => import('@/views/KnowledgeList.vue'), meta: { title: '知识库管理' } },
  { path: '/knowledge/:code', name: 'KnowledgeDetail', component: () => import('@/views/KnowledgeDetail.vue'), meta: { title: '知识库详情' } },
  { path: '/schema/datasources', name: 'DatasourceList', component: () => import('@/views/DatasourceList.vue'), meta: { title: '数据源管理' } },
  { path: '/schema/tables', name: 'TableSchemaList', component: () => import('@/views/TableSchemaList.vue'), meta: { title: '表结构管理' } },
  { path: '/schema/tables/edit', name: 'TableSchemaEdit', component: () => import('@/views/TableSchemaEdit.vue'), meta: { title: '编辑表结构' } }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - QueryGenie` : 'QueryGenie'
  next()
})

export default router
