package com.netflix.spinnaker.keel.ec2.jackson

import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer
import tools.jackson.databind.node.ArrayNode
import com.netflix.spinnaker.keel.api.ec2.ServerGroup.ActiveServerGroupImage
import com.netflix.spinnaker.keel.clouddriver.model.extractBaseImageName
import org.springframework.boot.jackson.JacksonComponent

@JacksonComponent
class ActiveServerGroupImageDeserializer :
  StdNodeBasedDeserializer<ActiveServerGroupImage>(ActiveServerGroupImage::class.java) {
  override fun convert(root: JsonNode, ctxt: DeserializationContext): ActiveServerGroupImage {
    val tags = root.get("tags") as ArrayNode

    return ActiveServerGroupImage(
      imageId = root.get("imageId").textValue(),
      appVersion = tags.getTag("appversion")?.substringBefore("/"),
      baseImageName = extractBaseImageName(root.get("description").textValue()),
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
