package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.netflix.spinnaker.keel.api.ec2.AllPorts
import com.netflix.spinnaker.keel.api.ec2.IngressPorts
import com.netflix.spinnaker.keel.api.ec2.PortRange

class IngressPortsDeserializer : StdNodeBasedDeserializer<IngressPorts>(IngressPorts::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext): IngressPorts =
    when (root) {
      is TextNode -> if (root.textValue() == "ALL") AllPorts else error("${root.textValue()} is not a valid value for port ranges")
      is ObjectNode -> root.run {
        PortRange(
          get("startPort").intValue(),
          get("endPort").intValue()
        )
      }
      else -> error("port ranges must be either an object with startPort and endPort fields, or the value 'ALL'")
    }
}
