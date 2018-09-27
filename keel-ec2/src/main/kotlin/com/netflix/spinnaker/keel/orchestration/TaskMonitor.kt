package com.netflix.spinnaker.keel.orchestration

import com.netflix.spinnaker.keel.api.AssetId
import com.netflix.spinnaker.keel.orca.TaskRef

interface TaskMonitor {
  fun monitorTask(assetId: AssetId, taskRef: TaskRef)

  fun isInProgress(assetId: AssetId): Boolean
}

