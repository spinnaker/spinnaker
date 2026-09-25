package com.netflix.spinnaker.keel.ec2.jackson

import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer
import tools.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.api.ec2.AllPorts
import com.netflix.spinnaker.keel.api.ec2.IngressPorts
import com.netflix.spinnaker.keel.api.ec2.PortRange
import org.springframework.boot.jackson.JacksonComponent


@JacksonComponent
class IngressPortsDeserializer : StdNodeBasedDeserializer<IngressPorts>(IngressPorts::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext): IngressPorts =
    when {
      root.isTextual -> if (root.textValue() == "ALL") AllPorts else error("${root.textValue()} is not a valid value for port ranges")
      root is ObjectNode -> root.run {
        PortRange(
          get("startPort").intValue(),
          get("endPort").intValue()
        )
      }
      else -> error("port ranges must be either an object with startPort and endPort fields, or the value 'ALL'")
    }
}
