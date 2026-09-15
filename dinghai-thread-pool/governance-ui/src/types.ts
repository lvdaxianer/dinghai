export interface Instance {
  instanceId: string
  baseUrl: string
  environment: string
  enabled: boolean
  version: string
  updatedAt: number
}

export interface Metric {
  instanceId: string
  sampledAt: number
  poolName: string
  active: number
  poolSize: number
  queueSize: number
  actualMaximum: number
  submitted: number
  completed: number
  rejected: number
  failed: number
  waitMillis: number
  executionMillis: number
  priority: string
  reliability: string
}

export interface Alert {
  alertKey: string
  status: string
  firstFiredAt: number
  lastFiredAt: number
  lastValue: string | null
  resolvedAt: number | null
}

export interface Gap {
  id: number
  instanceId: string
  requestedCursor: string | null
  oldestCursor: string | null
  detectedAt: number
  recoveryStatus: string
}
