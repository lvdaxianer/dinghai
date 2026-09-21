<script setup lang="ts">
import { onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import type { Metric } from '@/types'

const props = defineProps<{ metrics: Metric[]; title: string; series: Array<{ name: string; key: keyof Metric; color: string }> }>()
const chartElement = shallowRef<HTMLDivElement>()
let chart: echarts.ECharts | undefined

function render() {
  if (!chart || !chartElement.value) return
  chart.setOption({
    animation: true,
    animationDuration: 500,
    grid: { left: 48, right: 18, top: 40, bottom: 28 },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(11, 18, 33, 0.95)',
      borderColor: 'rgba(148, 163, 184, 0.2)',
      textStyle: { color: '#e2e8f0', fontSize: 12 },
      padding: [10, 14],
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(148, 163, 184, 0.3)', type: 'dashed' } }
    },
    legend: {
      top: 8,
      right: 8,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#94a3b8', fontSize: 11 }
    },
    xAxis: {
      type: 'category',
      data: props.metrics.map(metric => new Date(metric.sampledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })),
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.15)' } },
      axisLabel: { color: '#64748b', fontSize: 11 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisLabel: { color: '#64748b', fontSize: 11 },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } }
    },
    series: props.series.map(item => ({
      name: item.name,
      type: 'line',
      smooth: true,
      showSymbol: false,
      sampling: 'lttb',
      lineStyle: { width: 2.5, color: item.color },
      itemStyle: { color: item.color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: `${item.color}33` },
          { offset: 1, color: `${item.color}05` }
        ])
      },
      data: props.metrics.map(metric => metric[item.key])
    }))
  })
}

onMounted(() => {
  if (chartElement.value) {
    chart = echarts.init(chartElement.value, undefined, { renderer: 'canvas' })
    render()
  }
  window.addEventListener('resize', render)
})
onBeforeUnmount(() => { chart?.dispose(); window.removeEventListener('resize', render) })
watch(() => props.metrics, render, { deep: true })
</script>

<template>
  <section class="chart-panel">
    <div class="panel-title">{{ title }}</div>
    <div ref="chartElement" class="chart" />
  </section>
</template>

<style scoped>
.chart-panel {
  min-width: 0;
  background: var(--dh-surface);
  border: 1px solid var(--dh-border);
  border-radius: var(--dh-radius);
  padding: 16px;
  box-shadow: var(--dh-shadow-card);
  transition: transform var(--dh-transition), box-shadow var(--dh-transition);
}

.chart-panel:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.panel-title {
  color: var(--dh-text);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
}

.chart { height: 248px; width: 100%; }
</style>
