package com.netflix.spinnaker.keel.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy

internal object TagVersionStrategySerializer : StdSerializer<TagVersionStrategy>(TagVersionStrategy::class.java) {
  override fun serialize(value: TagVersionStrategy, gen: JsonGenerator, provider: SerializerProvider) {
    gen.writeString(value.friendlyName)
  }
}
