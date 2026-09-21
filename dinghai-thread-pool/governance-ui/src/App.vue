<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Refresh, WarningFilled, Monitor, Cpu, Timer, Histogram, TrendCharts } from '@element-plus/icons-vue'
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

const summaryCards = computed(() => [
  { label: '活跃线程', value: latest.value?.active ?? 0, unit: `/ ${latest.value?.actualMaximum ?? 0}`, hint: `${utilization.value}% 利用率`, icon: Cpu, tone: 'success' },
  { label: '队列深度', value: latest.value?.queueSize ?? 0, unit: '', hint: `${queueUtilization.value}% 队列负载`, icon: Histogram, tone: 'warning' },
  { label: '平均执行', value: averageExecution.value, unit: 'ms', hint: `累计完成 ${totals.value.completed.toLocaleString()}`, icon: Timer, tone: 'accent' },
  { label: '平均等待', value: averageWait.value, unit: 'ms', hint: `累计提交 ${totals.value.submitted.toLocaleString()}`, icon: TrendCharts, tone: 'purple' },
  { label: '失败率', value: failedRate.value, unit: '%', hint: `拒绝 ${totals.value.rejected} / 失败 ${totals.value.failed}`, icon: WarningFilled, tone: 'danger' }
])
</script>

<template>
  <main class="console-shell">
    <header class="topbar">
      <div class="brand">
        <div class="logo"><Monitor /></div>
        <div>
          <div class="eyebrow">DINGHAI / CONTROL PLANE</div>
          <h1>线程池总控</h1>
        </div>
      </div>
      <div class="refresh-state">
        <span v-if="lastUpdated" class="last-updated">更新于 {{ new Date(lastUpdated).toLocaleTimeString() }}</span>
        <el-button :icon="Refresh" circle :loading="loading" aria-label="刷新数据" @click="refresh" />
      </div>
    </header>

    <section class="filters" aria-label="数据筛选">
      <div class="filter-group">
        <el-select v-model="selectedInstance" clearable placeholder="全部实例" @change="refresh">
          <el-option v-for="item in instances" :key="item.instanceId" :label="`${item.instanceId} · ${item.environment}`" :value="item.instanceId" />
        </el-select>
        <el-select v-model="selectedPool" clearable placeholder="全部线程池" @change="refresh">
          <el-option v-for="pool in pools" :key="pool" :label="pool" :value="pool" />
        </el-select>
      </div>
      <div class="filter-meta">
        <span class="range-label">近 30 天历史采样</span>
        <span class="status-pill" :class="error ? 'is-error' : 'is-ok'">
          <span class="status-dot" />
          {{ error ? '连接异常' : '数据正常' }}
        </span>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="error-alert">
      <template #default><el-button size="small" @click="refresh">重试</el-button></template>
    </el-alert>
    <el-skeleton v-if="loading && !metrics.length" :rows="8" animated class="page-skeleton" />

    <template v-else>
      <section class="summary-grid" aria-label="核心指标">
        <article v-for="card in summaryCards" :key="card.label" class="metric-card" :class="`tone-${card.tone}`">
          <div class="metric-header">
            <span class="metric-icon"><component :is="card.icon" /></span>
            <span class="metric-label">{{ card.label }}</span>
          </div>
          <strong>{{ card.value }}<small v-if="card.unit">{{ card.unit }}</small></strong>
          <em>{{ card.hint }}</em>
          <div class="card-shine" />
        </article>
      </section>

      <section v-if="metrics.length" class="charts-grid">
        <MetricChart title="线程活动与上限" :metrics="metrics" :series="[{ name: '活跃线程', key: 'active', color: '#22c55e' }, { name: '最大线程', key: 'actualMaximum', color: '#38bdf8' }]" />
        <MetricChart title="队列与执行耗时" :metrics="metrics" :series="[{ name: '队列深度', key: 'queueSize', color: '#f59e0b' }, { name: '累计执行(ms)', key: 'executionMillis', color: '#a78bfa' }]" />
        <MetricChart title="完成、拒绝与失败" :metrics="metrics" :series="[{ name: '完成', key: 'completed', color: '#22c55e' }, { name: '拒绝', key: 'rejected', color: '#f59e0b' }, { name: '失败', key: 'failed', color: '#ef4444' }]" />
        <MetricChart title="等待与执行时间" :metrics="metrics" :series="[{ name: '累计等待(ms)', key: 'waitMillis', color: '#38bdf8' }, { name: '累计执行(ms)', key: 'executionMillis', color: '#a78bfa' }]" />
      </section>

      <section class="lower-grid">
        <article class="table-panel">
          <div class="panel-title"><TrendCharts /> 累计执行耗时 Top 5</div>
          <div v-for="(metric, index) in topPools" :key="`${metric.instanceId}-${metric.poolName}-${metric.sampledAt}`" class="slow-row">
            <div class="row-rank">{{ index + 1 }}</div>
            <div class="row-body">
              <b>{{ metric.poolName }}</b>
              <span>{{ metric.instanceId }}</span>
            </div>
            <strong>{{ metric.executionMillis.toLocaleString() }} ms</strong>
          </div>
          <el-empty v-if="!topPools.length" description="暂无采样" :image-size="64" />
        </article>
        <article class="table-panel">
          <div class="panel-title"><WarningFilled /> 告警与采集缺口</div>
          <div v-for="alert in alerts" :key="alert.alertKey" class="alert-row">
            <el-tag :type="alert.status === 'OPEN' ? 'danger' : 'success'" size="small" effect="dark">{{ alert.status }}</el-tag>
            <span>{{ alert.alertKey }}</span>
            <span class="alert-time">{{ new Date(alert.lastFiredAt).toLocaleString() }}</span>
          </div>
          <div v-for="gap in gaps" :key="gap.id" class="alert-row">
            <el-tag type="warning" size="small" effect="dark">{{ gap.recoveryStatus }}</el-tag>
            <span>{{ gap.instanceId }} cursor 缺口</span>
            <span class="alert-time">{{ new Date(gap.detectedAt).toLocaleString() }}</span>
          </div>
          <el-empty v-if="!alerts.length && !gaps.length" description="暂无告警或缺口" :image-size="64" />
        </article>
      </section>
      <MetricTable :metrics="metrics" />
    </template>
  </main>
</template>

<style scoped>
.console-shell {
  min-height: 100vh;
  padding: 28px 32px 40px;
  color: var(--dh-text);
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 26px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.logo {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.18), rgba(167, 139, 250, 0.12));
  border: 1px solid var(--dh-border);
  box-shadow: 0 0 20px rgba(56, 189, 248, 0.12);
  color: var(--dh-accent);
  font-size: 20px;
}

.eyebrow {
  color: var(--dh-accent);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

h1 {
  margin: 5px 0 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(90deg, #f8fafc 60%, #38bdf8);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.refresh-state {
  display: flex;
  align-items: center;
  gap: 12px;
}

.last-updated {
  color: var(--dh-text-muted);
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  background: var(--dh-surface);
  border: 1px solid var(--dh-border);
}

.filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 60px;
  padding: 12px 16px;
  margin-bottom: 20px;
  background: rgba(11, 18, 33, 0.7);
  backdrop-filter: blur(12px);
  border: 1px solid var(--dh-border);
  border-radius: var(--dh-radius);
  box-shadow: var(--dh-shadow);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filters :deep(.el-select) { width: 220px; }

.filter-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-left: auto;
}

.range-label {
  color: var(--dh-text-secondary);
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid var(--dh-border);
  background: var(--dh-surface);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.is-ok { color: var(--dh-success); border-color: rgba(34, 197, 94, 0.25); }
.is-ok .status-dot { background: var(--dh-success); box-shadow: 0 0 8px var(--dh-success); }
.is-error { color: var(--dh-danger); border-color: rgba(248, 113, 113, 0.25); }
.is-error .status-dot { background: var(--dh-danger); box-shadow: 0 0 8px var(--dh-danger); animation: none; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

.error-alert {
  margin-bottom: 18px;
  border-radius: var(--dh-radius-sm);
}

.page-skeleton {
  padding: 18px;
  background: var(--dh-surface);
  border: 1px solid var(--dh-border);
  border-radius: var(--dh-radius);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--dh-border);
  background: var(--dh-surface);
  border-radius: var(--dh-radius);
  padding: 18px;
  min-height: 124px;
  box-shadow: var(--dh-shadow-card);
  transition: transform var(--dh-transition), box-shadow var(--dh-transition), border-color var(--dh-transition);
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.metric-card.tone-success { border-top: 2px solid var(--dh-success); }
.metric-card.tone-success .metric-icon { color: var(--dh-success); background: var(--dh-success-soft); }
.metric-card.tone-warning { border-top: 2px solid var(--dh-warning); }
.metric-card.tone-warning .metric-icon { color: var(--dh-warning); background: var(--dh-warning-soft); }
.metric-card.tone-accent { border-top: 2px solid var(--dh-accent); }
.metric-card.tone-accent .metric-icon { color: var(--dh-accent); background: rgba(56, 189, 248, 0.12); }
.metric-card.tone-purple { border-top: 2px solid var(--dh-purple); }
.metric-card.tone-purple .metric-icon { color: var(--dh-purple); background: rgba(167, 139, 250, 0.12); }
.metric-card.tone-danger { border-top: 2px solid var(--dh-danger); }
.metric-card.tone-danger .metric-icon { color: var(--dh-danger); background: var(--dh-danger-soft); }

.metric-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.metric-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  font-size: 16px;
}

.metric-label {
  color: var(--dh-text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.metric-card strong {
  display: block;
  font-size: 30px;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: -0.02em;
}

.metric-card small {
  font-size: 13px;
  color: var(--dh-text-muted);
  font-weight: 500;
  margin-left: 5px;
}

.metric-card em {
  display: block;
  margin-top: 8px;
  color: var(--dh-text-secondary);
  font-size: 12px;
  font-style: normal;
}

.card-shine {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0;
  background: radial-gradient(600px circle at var(--mouse-x, 50%) var(--mouse-y, 0%), rgba(255, 255, 255, 0.06), transparent 40%);
  transition: opacity var(--dh-transition);
}

.metric-card:hover .card-shine {
  opacity: 1;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.lower-grid {
  display: flex;
  align-items: stretch;
  gap: 16px;
  margin-bottom: 16px;
}

.lower-grid > * { flex: 1; min-width: 0; }

.table-panel {
  background: var(--dh-surface);
  border: 1px solid var(--dh-border);
  border-radius: var(--dh-radius);
  padding: 18px;
  box-shadow: var(--dh-shadow-card);
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--dh-text);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.panel-title .el-icon { color: var(--dh-accent); font-size: 16px; }

.slow-row, .alert-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 46px;
  padding: 8px 4px;
  border-top: 1px solid var(--dh-border);
  transition: background var(--dh-transition);
}

.slow-row:hover, .alert-row:hover {
  background: rgba(255, 255, 255, 0.02);
  border-radius: var(--dh-radius-sm);
}

.row-rank {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--dh-text-muted);
  background: var(--dh-surface-2);
}

.slow-row:nth-child(2) .row-rank { color: #fbbf24; background: rgba(251, 191, 36, 0.12); }

.row-body {
  flex: 1;
  min-width: 0;
}

.slow-row b {
  display: block;
  font-size: 13px;
  color: var(--dh-text);
}

.slow-row span, .alert-row span {
  color: var(--dh-text-secondary);
  font-size: 12px;
}

.slow-row strong {
  color: #fbbf24;
  font-size: 13px;
  font-weight: 600;
}

.alert-row span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alert-time {
  color: var(--dh-text-muted) !important;
  font-size: 11px !important;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .charts-grid { grid-template-columns: 1fr; }
  .lower-grid { flex-direction: column; }
}

@media (max-width: 760px) {
  .console-shell { padding: 18px; }
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filters { flex-wrap: wrap; }
  .filter-group { width: 100%; }
  .filters :deep(.el-select) { flex: 1; width: auto; }
  .filter-meta { width: 100%; justify-content: space-between; margin-left: 0; }
}

@media (max-width: 520px) {
  .topbar { align-items: flex-start; gap: 12px; }
  .last-updated { display: none; }
  .summary-grid { grid-template: 1fr; }
  .filters :deep(.el-select) { width: 100%; }
}
</style>
