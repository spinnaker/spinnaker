package com.netflix.spinnaker.orca.remote

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPointDefinition
import com.netflix.spinnaker.orca.remote.model.RemoteStageExtensionPointConfig

@Beta
class RemoteStageExtensionPointDefinition : RemoteExtensionPointDefinition {
  override fun type(): String = "stage"
  override fun configType(): Class<out RemoteStageExtensionPointConfig> = RemoteStageExtensionPointConfig::class.java
}
