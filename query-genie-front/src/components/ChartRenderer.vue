<template>
  <div ref="chartEl" class="chart-renderer"></div>
</template>

<script>
import * as echarts from 'echarts'

/**
 * ECharts 图表渲染组件。
 *
 * Props:
 *   option - ECharts option 对象
 *   height - 图表高度（默认 300px）
 *
 * 通过 ResizeObserver 自动响应容器宽度变化。
 */
export default {
  name: 'ChartRenderer',
  props: {
    option: { type: Object, required: true },
    height: { type: [Number, String], default: 300 }
  },
  data() {
    return { _chart: null, _resizeObserver: null }
  },
  computed: {
    chartHeight() {
      return typeof this.height === 'number' ? this.height + 'px' : this.height
    }
  },
  mounted() {
    this.$el.style.height = this.chartHeight
    this._chart = echarts.init(this.$refs.chartEl)
    this._chart.setOption(this.option)
    if (typeof ResizeObserver !== 'undefined') {
      this._resizeObserver = new ResizeObserver(() => {
        if (this._chart) this._chart.resize()
      })
      this._resizeObserver.observe(this.$el)
    }
  },
  watch: {
    option(newOpt) {
      if (this._chart) this._chart.setOption(newOpt, true)
    }
  },
  beforeDestroy() {
    if (this._resizeObserver) this._resizeObserver.disconnect()
    if (this._chart) { this._chart.dispose(); this._chart = null }
  }
}
</script>

<style scoped>
.chart-renderer {
  width: 100%;
  min-height: 200px;
  margin: 0.8em 0;
}
</style>
