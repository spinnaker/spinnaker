package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.netflix.spinnaker.keel.api.ec2.AllPorts
import com.netflix.spinnaker.keel.api.ec2.IngressPorts
import com.netflix.spinnaker.keel.api.ec2.PortRange
import org.springframework.boot.jackson.JsonComponent

@JsonComponent
class IngressPortsSerializer : StdSerializer<IngressPorts>(IngressPorts::class.java) {
  override fun serialize(value: IngressPorts, gen: JsonGenerator, provider: SerializerProvider) {
    when (value) {
      is PortRange -> gen.apply {
        writeStartObject()
        writeObjectField("startPort", value.startPort)
        writeObjectField("endPort", value.endPort)
        writeEndObject()
      }
      is AllPorts -> gen.writeString("ALL")
    }
  }
}
