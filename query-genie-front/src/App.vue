<template>
  <div id="app" class="app-wrap">
    <el-container>
      <el-aside width="240px" class="app-aside">
        <div class="aside-inner">
          <div class="logo-block">
            <span class="logo-mark" aria-hidden="true" />
            <div class="logo-text">
              <span class="logo-title">QueryGenie</span>
              <span class="logo-sub">知识检索与问答</span>
            </div>
          </div>
          <nav class="app-nav" aria-label="主导航">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="nav-item"
              :class="{ 'is-active': isNavActive(item) }"
            >
              <i :class="['nav-icon', item.icon]" />
              <span class="nav-label">{{ item.label }}</span>
            </router-link>
          </nav>
          <div class="theme-panel">
            <div class="panel-title">
              <i class="el-icon-brush" />
              <span>主题</span>
            </div>
            <div class="theme-grid">
              <button
                v-for="item in themeOptions"
                :key="item.key"
                type="button"
                class="theme-dot"
                :class="{ active: currentThemeKey === item.key }"
                :title="item.name"
                :aria-label="item.name"
                @click="applyTheme(item.key)"
              >
                <span class="dot-inner" :style="{ background: item.preview }" />
              </button>
            </div>
            <div class="theme-name">{{ currentThemeName }}</div>
          </div>
        </div>
      </el-aside>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script>
const THEME_KEY = 'query-genie-theme'
const themeMap = {
  default: {
    name: '默认主题',
    preview: 'linear-gradient(135deg, #409eff 0%, #67c23a 100%)',
    vars: {
      '--qg-bg-page': '#f3f5f8',
      '--qg-bg-card': '#ffffff',
      '--qg-bg-card-soft': '#f8fafc',
      '--qg-text-primary': '#1f2937',
      '--qg-text-secondary': '#6b7280',
      '--qg-border-subtle': '#e5e7eb',
      '--qg-primary': '#409eff',
      '--qg-primary-weak': '#eaf3ff',
      '--qg-aside-bg': '#171b24',
      '--qg-aside-text': '#b9c0ca',
      '--qg-aside-active': '#5aa9ff',
      '--qg-shadow': '0 12px 40px rgba(15, 23, 42, 0.06)',
      '--qg-radius-lg': '22px',
      '--qg-radius-md': '14px',
      '--qg-code-bg': '#0f172a',
      '--qg-code-text': '#e2e8f0',
      '--qg-nav-hover-bg': 'rgba(255, 255, 255, 0.06)',
      '--qg-nav-active-bg': 'rgba(90, 169, 255, 0.14)',
      '--qg-bubble-user-border': 'rgba(64, 158, 255, 0.22)'
    }
  },
  light: {
    name: '浅色主题',
    preview: 'linear-gradient(135deg, #e5e7eb 0%, #ffffff 100%)',
    vars: {
      '--qg-bg-page': '#f2f4f7',
      '--qg-bg-card': '#ffffff',
      '--qg-bg-card-soft': '#f6f7f9',
      '--qg-text-primary': '#1f2937',
      '--qg-text-secondary': '#6b7280',
      '--qg-border-subtle': '#d9dde5',
      '--qg-primary': '#4b5563',
      '--qg-primary-weak': '#eef1f5',
      '--qg-aside-bg': '#3f4652',
      '--qg-aside-text': '#d7dbe2',
      '--qg-aside-active': '#ffffff',
      '--qg-shadow': '0 12px 40px rgba(17, 24, 39, 0.07)',
      '--qg-radius-lg': '22px',
      '--qg-radius-md': '14px',
      '--qg-code-bg': '#1f2937',
      '--qg-code-text': '#f3f4f6',
      '--qg-nav-hover-bg': 'rgba(255, 255, 255, 0.08)',
      '--qg-nav-active-bg': 'rgba(255, 255, 255, 0.12)',
      '--qg-bubble-user-border': 'rgba(75, 85, 99, 0.2)'
    }
  },
  dark: {
    name: '深色主题',
    preview: 'linear-gradient(135deg, #111827 0%, #0b1f3a 100%)',
    vars: {
      '--qg-bg-page': '#0d1117',
      '--qg-bg-card': '#161b22',
      '--qg-bg-card-soft': '#1d2430',
      '--qg-text-primary': '#e6edf3',
      '--qg-text-secondary': '#9aa4b2',
      '--qg-border-subtle': '#2b3442',
      '--qg-primary': '#3b82f6',
      '--qg-primary-weak': '#1d2b45',
      '--qg-aside-bg': '#0b1628',
      '--qg-aside-text': '#9db2ce',
      '--qg-aside-active': '#60a5fa',
      '--qg-shadow': '0 16px 48px rgba(0, 0, 0, 0.35)',
      '--qg-radius-lg': '22px',
      '--qg-radius-md': '14px',
      '--qg-code-bg': '#010409',
      '--qg-code-text': '#c9d1d9',
      '--qg-nav-hover-bg': 'rgba(255, 255, 255, 0.05)',
      '--qg-nav-active-bg': 'rgba(59, 130, 246, 0.2)',
      '--qg-bubble-user-border': 'rgba(59, 130, 246, 0.35)'
    }
  },
  ocean: {
    name: '海洋蓝',
    preview: 'linear-gradient(135deg, #0ea5e9 0%, #14b8a6 100%)',
    vars: {
      '--qg-bg-page': '#f0f9ff',
      '--qg-bg-card': '#ffffff',
      '--qg-bg-card-soft': '#ecfeff',
      '--qg-text-primary': '#0f172a',
      '--qg-text-secondary': '#475569',
      '--qg-border-subtle': '#cfe8f2',
      '--qg-primary': '#0ea5e9',
      '--qg-primary-weak': '#e0f2fe',
      '--qg-aside-bg': '#0c2437',
      '--qg-aside-text': '#bae6fd',
      '--qg-aside-active': '#22d3ee',
      '--qg-shadow': '0 12px 40px rgba(14, 116, 144, 0.12)',
      '--qg-radius-lg': '22px',
      '--qg-radius-md': '14px',
      '--qg-code-bg': '#0c1929',
      '--qg-code-text': '#e0f2fe',
      '--qg-nav-hover-bg': 'rgba(255, 255, 255, 0.06)',
      '--qg-nav-active-bg': 'rgba(14, 165, 233, 0.22)',
      '--qg-bubble-user-border': 'rgba(14, 165, 233, 0.28)'
    }
  },
  grape: {
    name: '葡萄紫',
    preview: 'linear-gradient(135deg, #8b5cf6 0%, #ec4899 100%)',
    vars: {
      '--qg-bg-page': '#faf5ff',
      '--qg-bg-card': '#ffffff',
      '--qg-bg-card-soft': '#fdf4ff',
      '--qg-text-primary': '#2e1065',
      '--qg-text-secondary': '#6b4f90',
      '--qg-border-subtle': '#ecd9ff',
      '--qg-primary': '#8b5cf6',
      '--qg-primary-weak': '#f3e8ff',
      '--qg-aside-bg': '#2e1065',
      '--qg-aside-text': '#ddd6fe',
      '--qg-aside-active': '#f9a8d4',
      '--qg-shadow': '0 12px 40px rgba(139, 92, 246, 0.14)',
      '--qg-radius-lg': '22px',
      '--qg-radius-md': '14px',
      '--qg-code-bg': '#1e1033',
      '--qg-code-text': '#f3e8ff',
      '--qg-nav-hover-bg': 'rgba(255, 255, 255, 0.07)',
      '--qg-nav-active-bg': 'rgba(139, 92, 246, 0.28)',
      '--qg-bubble-user-border': 'rgba(139, 92, 246, 0.3)'
    }
  }
}

export default {
  name: 'App',
  data() {
    return {
      currentThemeKey: 'default',
      themeOptions: Object.keys(themeMap).map(key => ({
        key,
        name: themeMap[key].name,
        preview: themeMap[key].preview
      })),
      navItems: [
        { path: '/search', label: '检索', icon: 'el-icon-search' },
        { path: '/qa', label: '智能问答', icon: 'el-icon-chat-dot-round' },
        { path: '/knowledge', label: '知识库', icon: 'el-icon-notebook-2', match: 'prefix' }
      ]
    }
  },
  computed: {
    currentTheme() {
      return themeMap[this.currentThemeKey] || themeMap.default
    },
    currentThemeName() {
      return this.currentTheme.name
    }
  },
  created() {
    const cached = window.localStorage.getItem(THEME_KEY)
    const target = cached && themeMap[cached] ? cached : 'default'
    this.applyTheme(target)
  },
  methods: {
    applyTheme(key) {
      const targetKey = themeMap[key] ? key : 'default'
      const vars = themeMap[targetKey].vars
      Object.keys(vars).forEach(cssVar => {
        document.documentElement.style.setProperty(cssVar, vars[cssVar])
      })
      this.currentThemeKey = targetKey
      window.localStorage.setItem(THEME_KEY, targetKey)
    },
    isNavActive(item) {
      const path = this.$route.path
      if (item.match === 'prefix') {
        return path === item.path || path.startsWith(`${item.path}/`)
      }
      return path === item.path
    }
  }
}
</script>

<style lang="scss" scoped>
.app-wrap {
  min-height: 100vh;
  background: var(--qg-bg-page);
}
.app-aside {
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  width: 240px;
  overflow: hidden;
  background: var(--qg-aside-bg);
  z-index: 100;
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  box-sizing: border-box;
}
.aside-inner {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px 14px 18px;
  box-sizing: border-box;
}
.logo-block {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 6px 22px;
  margin-bottom: 4px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.logo-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(145deg, var(--qg-aside-active) 0%, var(--qg-primary) 100%);
  opacity: 0.95;
  flex-shrink: 0;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.2);
}
.logo-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.logo-title {
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #fff;
  line-height: 1.2;
}
.logo-sub {
  font-size: 11px;
  color: var(--qg-aside-text);
  opacity: 0.92;
  line-height: 1.3;
}
.app-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 0 12px;
  min-height: 0;
  overflow-y: auto;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  text-decoration: none;
  color: var(--qg-aside-text);
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s ease, color 0.15s ease;
  border: 1px solid transparent;
  .nav-icon {
    font-size: 18px;
    opacity: 0.9;
  }
  .nav-label {
    letter-spacing: 0.02em;
  }
  &:hover {
    background: var(--qg-nav-hover-bg);
    color: #fff;
  }
  &.is-active {
    background: var(--qg-nav-active-bg);
    color: var(--qg-aside-active);
    border-color: rgba(255, 255, 255, 0.08);
  }
}
.app-main {
  margin-left: 240px;
  min-height: 100vh;
  overflow: auto;
  padding: 12px 24px 28px;
  box-sizing: border-box;
}
.theme-panel {
  flex-shrink: 0;
  margin-top: auto;
  padding: 14px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  .panel-title {
    display: flex;
    align-items: center;
    color: var(--qg-aside-text);
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    margin-bottom: 12px;
    opacity: 0.95;
    i {
      margin-right: 8px;
      color: var(--qg-aside-active);
      font-size: 14px;
    }
  }
  .theme-grid {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 8px;
  }
  .theme-dot {
    border: 0;
    background: transparent;
    padding: 0;
    cursor: pointer;
    width: 30px;
    height: 30px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transition: transform 0.18s ease, box-shadow 0.18s ease;
    .dot-inner {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      border: 2px solid rgba(255, 255, 255, 0.72);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
    }
    &:hover {
      transform: translateY(-2px);
    }
    &.active {
      box-shadow: 0 0 0 2px var(--qg-aside-active);
    }
  }
  .theme-name {
    margin-top: 12px;
    color: var(--qg-aside-text);
    font-size: 12px;
    opacity: 0.88;
  }
}
</style>

<style>
:root {
  --qg-bg-page: #f3f5f8;
  --qg-bg-card: #ffffff;
  --qg-text-primary: #1f2937;
  --qg-text-secondary: #6b7280;
  --qg-border-subtle: #e5e7eb;
  --qg-primary: #409eff;
  --qg-primary-weak: #eaf3ff;
  --qg-bg-card-soft: #f8fafc;
  --qg-aside-bg: #171b24;
  --qg-aside-text: #b9c0ca;
  --qg-aside-active: #5aa9ff;
  --qg-shadow: 0 12px 40px rgba(15, 23, 42, 0.06);
  --qg-radius-lg: 22px;
  --qg-radius-md: 14px;
  --qg-code-bg: #0f172a;
  --qg-code-text: #e2e8f0;
  --qg-nav-hover-bg: rgba(255, 255, 255, 0.06);
  --qg-nav-active-bg: rgba(90, 169, 255, 0.14);
  --qg-bubble-user-border: rgba(64, 158, 255, 0.22);
}
body {
  margin: 0;
  background: var(--qg-bg-page);
  color: var(--qg-text-primary);
  transition: background-color .2s ease, color .2s ease;
}
#app {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.el-main,
.search-top,
.chat-input-wrap {
  background: transparent !important;
}
.qg-page {
  max-width: 1320px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}
.el-card {
  border-radius: var(--qg-radius-md) !important;
  border: 1px solid var(--qg-border-subtle) !important;
}
.el-card,
.qa-main,
.qa-sidebar,
.composer {
  background: var(--qg-bg-card) !important;
  color: var(--qg-text-primary);
  border-color: var(--qg-border-subtle) !important;
  box-shadow: var(--qg-shadow) !important;
}
.el-input__inner,
.el-textarea__inner,
.el-select .el-input__inner {
  background: var(--qg-bg-card-soft) !important;
  color: var(--qg-text-primary) !important;
  border-color: var(--qg-border-subtle) !important;
}
.el-dialog,
.el-drawer {
  background: var(--qg-bg-card) !important;
}
.el-dialog__title,
.el-drawer__header {
  color: var(--qg-text-primary) !important;
}
.el-dialog__body,
.el-drawer__body,
.el-form-item__label,
.el-radio,
.el-checkbox,
.el-pagination,
.el-table,
.el-table th,
.el-table tr,
.el-table td {
  color: var(--qg-text-primary) !important;
}
.el-tabs__nav-wrap::after {
  background-color: var(--qg-border-subtle) !important;
}
.el-tabs__item {
  color: var(--qg-text-secondary) !important;
}
.el-tabs__item.is-active {
  color: var(--qg-primary) !important;
}
.el-tabs__active-bar {
  background-color: var(--qg-primary) !important;
}
.el-table,
.el-table__expanded-cell,
.el-table th.el-table__cell,
.el-table tr,
.el-table td,
.el-table__body-wrapper,
.el-table__header-wrapper {
  background-color: var(--qg-bg-card) !important;
  border-color: var(--qg-border-subtle) !important;
}
.el-table--striped .el-table__body tr.el-table__row--striped td {
  background: var(--qg-bg-card-soft) !important;
}
.el-table td.el-table__cell,
.el-table th.el-table__cell,
.el-table::before {
  border-color: var(--qg-border-subtle) !important;
}
.el-button {
  border-color: var(--qg-border-subtle);
  color: var(--qg-text-primary);
  background: var(--qg-bg-card);
}
.el-button--text {
  color: var(--qg-primary) !important;
  border-color: transparent !important;
  background: transparent !important;
  padding-left: 0;
  padding-right: 0;
}
.el-button--text:hover,
.el-button--text:focus {
  color: var(--qg-primary) !important;
  border-color: transparent !important;
  background: transparent !important;
  opacity: 0.9;
}
.el-button--primary {
  background-color: var(--qg-primary) !important;
  border-color: var(--qg-primary) !important;
  color: #fff !important;
}
.el-button--primary:hover,
.el-button--primary:focus {
  filter: brightness(1.05);
}
.el-radio-button__inner {
  background-color: var(--qg-bg-card-soft) !important;
  color: var(--qg-text-primary) !important;
  border-color: var(--qg-border-subtle) !important;
}
.el-radio-button:first-child .el-radio-button__inner {
  border-left-color: var(--qg-border-subtle) !important;
}
.el-radio-button__orig-radio:checked + .el-radio-button__inner {
  background-color: var(--qg-primary) !important;
  border-color: var(--qg-primary) !important;
  color: #fff !important;
  box-shadow: -1px 0 0 0 var(--qg-primary) !important;
}
.el-collapse {
  border-color: var(--qg-border-subtle) !important;
}
.el-collapse-item__header {
  background-color: transparent !important;
  color: var(--qg-text-primary) !important;
  border-bottom-color: var(--qg-border-subtle) !important;
}
.el-collapse-item__wrap {
  background-color: transparent !important;
  border-bottom-color: var(--qg-border-subtle) !important;
}
.qg-danger-text {
  color: #f56c6c !important;
}
</style>
