package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer
import com.netflix.spinnaker.keel.api.ResourceId

class ResourceIdDeserializer : FromStringDeserializer<ResourceId>(ResourceId::class.java) {
  override fun _deserialize(value: String, ctxt: DeserializationContext) =
    ResourceId(value)
}
