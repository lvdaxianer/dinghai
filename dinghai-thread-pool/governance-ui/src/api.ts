import type { Alert, Gap, Instance, Metric } from './types'

async function request<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (response.status === 403) throw new Error('总控接口拒绝访问，请检查授权策略')
  if (!response.ok) throw new Error(`总控接口请求失败（${response.status}）`)
  return response.json() as Promise<T>
}

export const governanceApi = {
  instances: () => request<Instance[]>('/governance/instances'),
  metrics: (params: { instanceId?: string; poolName?: string; from: number; to: number }) => {
    const query = new URLSearchParams({ from: String(params.from), to: String(params.to) })
    if (params.instanceId) query.set('instanceId', params.instanceId)
    if (params.poolName) query.set('poolName', params.poolName)
    return request<Metric[]>(`/governance/metrics?${query.toString()}`)
  },
  alerts: () => request<Alert[]>('/governance/alerts'),
  gaps: (instanceId: string) => request<Gap[]>(`/governance/gaps?instanceId=${encodeURIComponent(instanceId)}`)
}
