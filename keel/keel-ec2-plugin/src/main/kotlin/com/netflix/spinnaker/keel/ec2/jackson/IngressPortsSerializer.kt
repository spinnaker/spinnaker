package com.netflix.spinnaker.keel.ec2.jackson

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ser.std.StdSerializer
import com.netflix.spinnaker.keel.api.ec2.AllPorts
import com.netflix.spinnaker.keel.api.ec2.IngressPorts
import com.netflix.spinnaker.keel.api.ec2.PortRange
import org.springframework.boot.jackson.JacksonComponent


@JacksonComponent
class IngressPortsSerializer : StdSerializer<IngressPorts>(IngressPorts::class.java) {
  override fun serialize(value: IngressPorts, gen: JsonGenerator, provider: SerializationContext) {
    when (value) {
      is PortRange -> gen.apply {
        writeStartObject()
        writeNumberProperty("startPort", value.startPort)
        writeNumberProperty("endPort", value.endPort)
        writeEndObject()
      }
      is AllPorts -> gen.writeString("ALL")
    }
  }
}
