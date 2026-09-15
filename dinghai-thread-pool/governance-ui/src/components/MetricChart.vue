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
    animation: false,
    grid: { left: 42, right: 16, top: 34, bottom: 28 },
    tooltip: { trigger: 'axis' },
    legend: { top: 4, textStyle: { color: '#93a4b8', fontSize: 11 } },
    xAxis: { type: 'category', data: props.metrics.map(metric => new Date(metric.sampledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })), axisLabel: { color: '#64748b' } },
    yAxis: { type: 'value', axisLabel: { color: '#64748b' }, splitLine: { lineStyle: { color: '#1e293b' } } },
    series: props.series.map(item => ({ name: item.name, type: 'line', smooth: true, showSymbol: false, data: props.metrics.map(metric => metric[item.key]), lineStyle: { color: item.color }, itemStyle: { color: item.color } }))
  })
}

onMounted(() => { if (chartElement.value) chart = echarts.init(chartElement.value); render(); window.addEventListener('resize', render) })
onBeforeUnmount(() => { chart?.dispose(); window.removeEventListener('resize', render) })
watch(() => props.metrics, render, { deep: true })
</script>

<template><section class="chart-panel"><div class="panel-title">{{ title }}</div><div ref="chartElement" class="chart" /></section></template>

<style scoped>
.chart-panel { min-width: 0; border: 1px solid #1e293b; background: #0f172a; padding: 14px; }
.panel-title { color: #dbeafe; font-size: 13px; font-weight: 600; margin-bottom: 4px; }
.chart { height: 238px; width: 100%; }
</style>
