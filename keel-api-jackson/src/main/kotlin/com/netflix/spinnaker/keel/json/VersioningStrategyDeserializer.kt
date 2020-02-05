package com.netflix.spinnaker.keel.json

import com.netflix.spinnaker.keel.api.DebianSemVerVersioningStrategy
import com.netflix.spinnaker.keel.api.DockerVersioningStrategy
import com.netflix.spinnaker.keel.api.VersioningStrategy

internal object VersioningStrategyDeserializer : PropertyNamePolymorphicDeserializer<VersioningStrategy>(VersioningStrategy::class.java) {
  override fun identifySubType(fieldNames: Collection<String>): Class<out VersioningStrategy> =
    when {
      "tagVersionStrategy" in fieldNames -> DockerVersioningStrategy::class.java
      else -> DebianSemVerVersioningStrategy::class.java
    }
}
