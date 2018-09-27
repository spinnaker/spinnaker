package com.netflix.spinnaker.keel.orchestration

internal object InMemoryTaskMonitorSpec : TaskMonitorSpec<InMemoryTaskMonitor>(
  { orcaService -> InMemoryTaskMonitor(orcaService) }
)
