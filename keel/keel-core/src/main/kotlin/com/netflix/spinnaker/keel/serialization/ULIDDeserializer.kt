package com.netflix.spinnaker.keel.serialization

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer
import de.huxhorn.sulky.ulid.ULID

class ULIDDeserializer : FromStringDeserializer<ULID.Value>(ULID.Value::class.java) {
  override fun _deserialize(value: String, ctxt: DeserializationContext): ULID.Value =
    ULID.parseULID(value)
}
