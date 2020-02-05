package com.netflix.spinnaker.keel.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.netflix.spinnaker.keel.api.TagVersionStrategy

internal object TagVersionStrategyDeserializer : StdDeserializer<TagVersionStrategy>(TagVersionStrategy::class.java) {
  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): TagVersionStrategy {
    val value = p.text
    return TagVersionStrategy
      .values()
      .find { it.friendlyName == value }
      ?: throw ctxt.weirdStringException(value, TagVersionStrategy::class.java, "not one of the values accepted for Enum class: %s")
  }
}
