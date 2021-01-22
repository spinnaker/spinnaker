package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.databind.node.ArrayNode
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.ActiveServerGroupImage
import org.springframework.boot.jackson.JsonComponent

@JsonComponent
class ActiveServerGroupImageDeserializer : StdNodeBasedDeserializer<ActiveServerGroupImage>(ActiveServerGroupImage::class.java) {
  override fun convert(root: JsonNode, ctxt: DeserializationContext): ActiveServerGroupImage {
    val tags = root.get("tags") as ArrayNode

    return ActiveServerGroupImage(
      imageId = root.get("imageId").textValue(),
      appVersion = tags.getTag("appversion")?.substringBefore("/"),
      baseImageVersion = tags.getTag("base_ami_version"),
      name = root.get("name").textValue(),
      imageLocation = root.get("imageLocation").textValue(),
      description = root.get("description").textValue()
    )
  }

  private fun ArrayNode.getTag(key: String) =
    find { it.get("key").textValue() == key }
      ?.get("value")
      ?.textValue()
}
