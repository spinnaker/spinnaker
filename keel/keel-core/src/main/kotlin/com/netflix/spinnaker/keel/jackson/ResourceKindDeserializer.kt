package com.netflix.spinnaker.keel.jackson

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind

class ResourceKindDeserializer : ValueDeserializer<ResourceKind>() {
  override fun deserialize(parser: JsonParser, context: DeserializationContext) =
    parseKind(parser.text)
}
