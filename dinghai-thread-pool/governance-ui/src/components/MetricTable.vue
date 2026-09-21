<script setup lang="ts">
import type { Metric } from '@/types'
defineProps<{ metrics: Metric[] }>()

function reliabilityType(value: string) {
  if (value === 'HIGH') return 'success'
  if (value === 'MEDIUM') return 'warning'
  return 'info'
}

function priorityType(value: string) {
  if (value === 'CRITICAL') return 'danger'
  if (value === 'HIGH') return 'warning'
  return 'info'
}
</script>

<template>
  <section class="table-panel">
    <div class="panel-title">采样明细</div>
    <el-table :data="metrics.slice(-30).reverse()" size="small" empty-text="暂无符合条件的采样" class="metric-table">
      <el-table-column prop="instanceId" label="实例" min-width="140" />
      <el-table-column prop="poolName" label="线程池" min-width="130" />
      <el-table-column label="采样时间" min-width="160">
        <template #default="scope">{{ new Date(scope.row.sampledAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column prop="active" label="活跃" width="80" align="center" />
      <el-table-column prop="poolSize" label="线程" width="80" align="center" />
      <el-table-column prop="queueSize" label="队列" width="80" align="center" />
      <el-table-column prop="completed" label="完成" width="90" align="center" />
      <el-table-column prop="rejected" label="拒绝" width="80" align="center" />
      <el-table-column prop="failed" label="失败" width="80" align="center" />
      <el-table-column prop="executionMillis" label="执行耗时(ms)" width="120" align="right" />
      <el-table-column prop="priority" label="优先级" width="90" align="center">
        <template #default="scope"><el-tag :type="priorityType(scope.row.priority)" size="small" effect="dark">{{ scope.row.priority }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="reliability" label="可靠性" width="90" align="center">
        <template #default="scope"><el-tag :type="reliabilityType(scope.row.reliability)" size="small" effect="dark">{{ scope.row.reliability }}</el-tag></template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.table-panel {
  background: var(--dh-surface);
  border: 1px solid var(--dh-border);
  border-radius: var(--dh-radius);
  padding: 18px;
  box-shadow: var(--dh-shadow-card);
}

.panel-title {
  color: var(--dh-text);
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.metric-table {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(17, 27, 46, 0.6);
  --el-table-border-color: var(--dh-border);
  --el-table-text-color: var(--dh-text);
  --el-table-header-text-color: var(--dh-text-secondary);
  --el-table-row-hover-bg-color: rgba(56, 189, 248, 0.06);
  border-radius: var(--dh-radius-sm);
  overflow: hidden;
}

.metric-table :deep(.el-table__header th) {
  font-weight: 600;
  font-size: 12px;
  letter-spacing: 0.02em;
}

.metric-table :deep(.el-table__body td) {
  font-size: 12px;
  padding: 10px 0;
}

.metric-table :deep(.el-tag--small) {
  font-weight: 600;
}
</style>
