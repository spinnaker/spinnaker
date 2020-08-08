package com.netflix.spinnaker.keel.api.plugins

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.persistence.KeelReadOnlyRepository
import com.netflix.spinnaker.kork.plugins.api.servicesdk.ServiceSdk

/**
 * A simple SDK that can be consumed by external plugins to access core Keel functionality.
 */
interface KeelServiceSdk : ServiceSdk {
  val repository: KeelReadOnlyRepository
  val taskLauncher: TaskLauncher
}
