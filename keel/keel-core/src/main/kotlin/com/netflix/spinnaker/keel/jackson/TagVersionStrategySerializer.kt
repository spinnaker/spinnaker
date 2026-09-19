package com.netflix.spinnaker.keel.jackson

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy

internal object TagVersionStrategySerializer : StdSerializer<TagVersionStrategy>(TagVersionStrategy::class.java) {
  override fun serialize(value: TagVersionStrategy, gen: JsonGenerator, provider: SerializationContext) {
    gen.writeString(value.friendlyName)
  }
}
