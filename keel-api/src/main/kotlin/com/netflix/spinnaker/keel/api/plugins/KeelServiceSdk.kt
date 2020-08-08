package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.persistence.KeelReadOnlyRepository

/**
 * A simple SDK that can be consumed by external plugins to access core Keel functionality.
 */
interface KeelServiceSdk {
  val repository: KeelReadOnlyRepository
  val taskLauncher: TaskLauncher
}
