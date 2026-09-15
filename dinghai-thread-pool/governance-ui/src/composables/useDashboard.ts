import { computed, shallowRef } from 'vue'
import { governanceApi } from '@/api'
import type { Alert, Gap, Instance, Metric } from '@/types'

export function useDashboard() {
  const instances = shallowRef<Instance[]>([])
  const metrics = shallowRef<Metric[]>([])
  const alerts = shallowRef<Alert[]>([])
  const gaps = shallowRef<Gap[]>([])
  const selectedInstance = shallowRef('')
  const selectedPool = shallowRef('')
  const loading = shallowRef(false)
  const error = shallowRef('')
  const lastUpdated = shallowRef<number | null>(null)
  const pools = computed(() => [...new Set(metrics.value.map(metric => metric.poolName))].sort())
  const latest = computed(() => metrics.value.at(-1))
  const totals = computed(() => metrics.value.reduce((sum, metric) => ({
    submitted: sum.submitted + metric.submitted,
    completed: sum.completed + metric.completed,
    rejected: sum.rejected + metric.rejected,
    failed: sum.failed + metric.failed,
    waitMillis: sum.waitMillis + metric.waitMillis,
    executionMillis: sum.executionMillis + metric.executionMillis
  }), { submitted: 0, completed: 0, rejected: 0, failed: 0, waitMillis: 0, executionMillis: 0 }))

  async function refresh() {
    loading.value = true
    error.value = ''
    try {
      instances.value = await governanceApi.instances()
      const now = Date.now()
      metrics.value = await governanceApi.metrics({ instanceId: selectedInstance.value || undefined, poolName: selectedPool.value || undefined, from: now - 30 * 24 * 60 * 60 * 1000, to: now })
      alerts.value = await governanceApi.alerts()
      const instanceIds = selectedInstance.value ? [selectedInstance.value] : instances.value.map(instance => instance.instanceId)
      const responses = await Promise.all(instanceIds.map(instanceId => governanceApi.gaps(instanceId)))
      gaps.value = responses.flat()
      lastUpdated.value = Date.now()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '无法读取总控数据'
    } finally {
      loading.value = false
    }
  }

  return { instances, metrics, alerts, gaps, selectedInstance, selectedPool, pools, latest, totals, loading, error, lastUpdated, refresh }
}
