package com.netflix.spinnaker.keel.jackson

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy

internal object TagVersionStrategyDeserializer : StdDeserializer<TagVersionStrategy>(TagVersionStrategy::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TagVersionStrategy {
    val value = p.text
    return TagVersionStrategy
      .values()
      .find { it.friendlyName == value || it.name == value }
      ?: throw ctxt.weirdStringException(value, TagVersionStrategy::class.java, "not one of the values accepted for Enum class: %s")
  }
}
