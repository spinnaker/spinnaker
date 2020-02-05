package com.netflix.spinnaker.keel.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.netflix.spinnaker.keel.api.TagVersionStrategy

internal object TagVersionStrategySerializer : StdSerializer<TagVersionStrategy>(TagVersionStrategy::class.java) {
  override fun serialize(value: TagVersionStrategy, gen: JsonGenerator, provider: SerializerProvider) {
    gen.writeString(value.friendlyName)
  }
}
