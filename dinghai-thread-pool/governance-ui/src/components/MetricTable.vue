<script setup lang="ts">
import type { Metric } from '@/types'
defineProps<{ metrics: Metric[] }>()
</script>

<template>
  <section class="table-panel">
    <div class="panel-title">采样明细</div>
    <el-table :data="metrics.slice(-30).reverse()" size="small" stripe empty-text="暂无符合条件的采样">
      <el-table-column prop="instanceId" label="实例" min-width="130" />
      <el-table-column prop="poolName" label="线程池" min-width="120" />
      <el-table-column label="采样时间" min-width="150"><template #default="scope">{{ new Date(scope.row.sampledAt).toLocaleString() }}</template></el-table-column>
      <el-table-column prop="active" label="活跃" width="74" />
      <el-table-column prop="poolSize" label="线程" width="74" />
      <el-table-column prop="queueSize" label="队列" width="74" />
      <el-table-column prop="completed" label="完成" width="88" />
      <el-table-column prop="rejected" label="拒绝" width="74" />
      <el-table-column prop="failed" label="失败" width="74" />
      <el-table-column prop="executionMillis" label="执行耗时(ms)" width="120" />
    </el-table>
  </section>
</template>

<style scoped>
.table-panel { border: 1px solid #1e293b; background: #0f172a; padding: 14px; overflow: hidden; }
.panel-title { color: #dbeafe; font-size: 13px; font-weight: 600; margin-bottom: 10px; }
:deep(.el-table) { --el-table-bg-color: #0f172a; --el-table-tr-bg-color: #0f172a; --el-table-header-bg-color: #111c2e; --el-table-border-color: #1e293b; --el-table-text-color: #cbd5e1; --el-table-header-text-color: #64748b; }
</style>
