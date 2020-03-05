package com.netflix.spinnaker.keel.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceKind.Companion.parseKind

class ResourceKindDeserializer : JsonDeserializer<ResourceKind>() {
  override fun deserialize(parser: JsonParser, context: DeserializationContext) =
    parseKind(parser.text)
}
