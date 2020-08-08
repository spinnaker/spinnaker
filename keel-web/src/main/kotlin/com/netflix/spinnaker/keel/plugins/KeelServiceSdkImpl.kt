package com.netflix.spinnaker.keel.plugins

import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.persistence.KeelReadOnlyRepository
import com.netflix.spinnaker.keel.api.plugins.KeelServiceSdk

class KeelServiceSdkImpl(
  override val repository: KeelReadOnlyRepository,
  override val taskLauncher: TaskLauncher
) : KeelServiceSdk
