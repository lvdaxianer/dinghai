<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import MetricChart from '@/components/MetricChart.vue'
import MetricTable from '@/components/MetricTable.vue'
import { useDashboard } from '@/composables/useDashboard'

const { instances, metrics, alerts, gaps, selectedInstance, selectedPool, pools, latest, totals, loading, error, lastUpdated, refresh } = useDashboard()
const utilization = computed(() => latest.value ? Math.round(latest.value.active / Math.max(latest.value.actualMaximum, 1) * 100) : 0)
const queueUtilization = computed(() => latest.value ? Math.round(latest.value.queueSize / Math.max(latest.value.queueSize + latest.value.poolSize, 1) * 100) : 0)
const averageExecution = computed(() => totals.value.completed ? Math.round(totals.value.executionMillis / totals.value.completed) : 0)
const averageWait = computed(() => totals.value.completed ? Math.round(totals.value.waitMillis / totals.value.completed) : 0)
const failedRate = computed(() => totals.value.submitted ? (totals.value.failed / totals.value.submitted * 100).toFixed(2) : '0.00')
const topPools = computed(() => [...metrics.value].sort((left, right) => right.executionMillis - left.executionMillis).slice(-5).reverse())
onMounted(refresh)
</script>

<template>
  <main class="console-shell">
    <header class="topbar">
      <div><div class="eyebrow">DINGHAI / CONTROL PLANE</div><h1>线程池总控</h1></div>
      <div class="refresh-state"><span v-if="lastUpdated">更新于 {{ new Date(lastUpdated).toLocaleTimeString() }}</span><el-button :icon="Refresh" circle :loading="loading" aria-label="刷新数据" @click="refresh" /></div>
    </header>

    <section class="filters" aria-label="数据筛选">
      <el-select v-model="selectedInstance" clearable placeholder="全部实例" @change="refresh"><el-option v-for="item in instances" :key="item.instanceId" :label="`${item.instanceId} · ${item.environment}`" :value="item.instanceId" /></el-select>
      <el-select v-model="selectedPool" clearable placeholder="全部线程池" @change="refresh"><el-option v-for="pool in pools" :key="pool" :label="pool" :value="pool" /></el-select>
      <span class="range-label">近 30 天历史采样</span>
      <span class="status-dot" :class="error ? 'is-error' : 'is-ok'" />
    </section>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><template #default><el-button size="small" @click="refresh">重试</el-button></template></el-alert>
    <el-skeleton v-if="loading && !metrics.length" :rows="8" animated />

    <template v-else>
      <section class="summary-grid" aria-label="核心指标">
        <article class="metric-card"><span>活跃线程</span><strong>{{ latest?.active ?? 0 }}<small>/ {{ latest?.actualMaximum ?? 0 }}</small></strong><em>{{ utilization }}% 利用率</em></article>
        <article class="metric-card"><span>队列深度</span><strong>{{ latest?.queueSize ?? 0 }}</strong><em>{{ queueUtilization }}% 队列负载</em></article>
        <article class="metric-card"><span>平均执行</span><strong>{{ averageExecution }}<small> ms</small></strong><em>累计完成 {{ totals.completed }}</em></article>
        <article class="metric-card"><span>平均等待</span><strong>{{ averageWait }}<small> ms</small></strong><em>累计提交 {{ totals.submitted }}</em></article>
        <article class="metric-card risk"><span>失败率</span><strong>{{ failedRate }}<small>%</small></strong><em>拒绝 {{ totals.rejected }} / 失败 {{ totals.failed }}</em></article>
      </section>

      <section v-if="metrics.length" class="charts-grid">
        <MetricChart title="线程活动与上限" :metrics="metrics" :series="[{ name: '活跃线程', key: 'active', color: '#22c55e' }, { name: '最大线程', key: 'actualMaximum', color: '#38bdf8' }]" />
        <MetricChart title="队列与执行耗时" :metrics="metrics" :series="[{ name: '队列深度', key: 'queueSize', color: '#f59e0b' }, { name: '累计执行(ms)', key: 'executionMillis', color: '#a78bfa' }]" />
        <MetricChart title="完成、拒绝与失败" :metrics="metrics" :series="[{ name: '完成', key: 'completed', color: '#22c55e' }, { name: '拒绝', key: 'rejected', color: '#f59e0b' }, { name: '失败', key: 'failed', color: '#ef4444' }]" />
        <MetricChart title="等待与执行时间" :metrics="metrics" :series="[{ name: '累计等待(ms)', key: 'waitMillis', color: '#38bdf8' }, { name: '累计执行(ms)', key: 'executionMillis', color: '#a78bfa' }]" />
      </section>

      <section class="lower-grid">
        <article class="table-panel"><div class="panel-title">累计执行耗时 Top 5</div><div v-for="metric in topPools" :key="`${metric.instanceId}-${metric.poolName}-${metric.sampledAt}`" class="slow-row"><div><b>{{ metric.poolName }}</b><span>{{ metric.instanceId }}</span></div><strong>{{ metric.executionMillis }} ms</strong></div><el-empty v-if="!topPools.length" description="暂无采样" :image-size="64" /></article>
        <article class="table-panel"><div class="panel-title"><WarningFilled /> 告警与采集缺口</div><div v-for="alert in alerts" :key="alert.alertKey" class="alert-row"><el-tag :type="alert.status === 'OPEN' ? 'danger' : 'success'" size="small">{{ alert.status }}</el-tag><span>{{ alert.alertKey }}</span></div><div v-for="gap in gaps" :key="gap.id" class="alert-row"><el-tag type="warning" size="small">{{ gap.recoveryStatus }}</el-tag><span>{{ gap.instanceId }} cursor 缺口</span></div><el-empty v-if="!alerts.length && !gaps.length" description="暂无告警或缺口" :image-size="64" /></article>
      </section>
      <MetricTable :metrics="metrics" />
    </template>
  </main>
</template>

<style scoped>
.console-shell { min-height: 100vh; padding: 24px; background: #08111f; color: #e2e8f0; }
.topbar, .filters, .refresh-state, .lower-grid, .slow-row, .alert-row { display: flex; align-items: center; }
.topbar { justify-content: space-between; margin-bottom: 22px; }
.eyebrow { color: #38bdf8; font-size: 11px; font-weight: 700; letter-spacing: 0; }
h1 { margin: 5px 0 0; font-size: 26px; letter-spacing: 0; }
.refresh-state { gap: 10px; color: #64748b; font-size: 12px; }
.filters { gap: 10px; min-height: 54px; border: 1px solid #1e293b; padding: 10px 12px; margin-bottom: 16px; background: #0f172a; }
.filters :deep(.el-select) { width: 210px; }.range-label { color: #94a3b8; font-size: 13px; margin-left: auto; }.status-dot { width: 8px; height: 8px; border-radius: 50%; }.is-ok { background: #22c55e; }.is-error { background: #ef4444; }
.summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin: 16px 0; }.metric-card { border: 1px solid #1e293b; background: #0f172a; padding: 14px; min-height: 108px; }.metric-card span, .metric-card em { display: block; color: #94a3b8; font-size: 12px; font-style: normal; }.metric-card strong { display: block; margin: 12px 0 7px; font-size: 25px; color: #f8fafc; }.metric-card small { font-size: 12px; color: #64748b; }.risk strong { color: #fca5a5; }
.charts-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }.lower-grid { align-items: stretch; gap: 12px; margin-bottom: 12px; }.lower-grid > * { flex: 1; }.table-panel { border: 1px solid #1e293b; background: #0f172a; padding: 14px; overflow: hidden; }.panel-title { color: #dbeafe; font-size: 13px; font-weight: 600; margin-bottom: 10px; }.slow-row, .alert-row { justify-content: space-between; gap: 8px; min-height: 39px; border-top: 1px solid #1e293b; }.slow-row b { display: block; font-size: 13px; }.slow-row span, .alert-row span { color: #94a3b8; font-size: 12px; }.slow-row strong { color: #fbbf24; font-size: 13px; }
@media (max-width: 980px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.charts-grid { grid-template-columns: 1fr; }.lower-grid { flex-direction: column; }.filters { flex-wrap: wrap; }.range-label { margin-left: 0; } }
@media (max-width: 560px) { .console-shell { padding: 14px; }.topbar { align-items: flex-start; }.refresh-state span { display: none; }.summary-grid { grid-template-columns: 1fr; }.filters :deep(.el-select) { width: 100%; } }
</style>
