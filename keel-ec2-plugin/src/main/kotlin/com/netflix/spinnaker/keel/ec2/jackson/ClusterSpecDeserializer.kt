package com.netflix.spinnaker.keel.ec2.jackson

import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec

class ClusterSpecDeserializer : StdNodeBasedDeserializer<ClusterSpec>(ClusterSpec::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext): ClusterSpec =
    with(context) {
      ClusterSpec(
        moniker = treeToValue(root.get("moniker")) ?: error("moniker is required"),
        imageProvider = treeToValue(root.get("imageProvider")),
        deployWith = treeToValue(root.get("deployWith")) ?: RedBlack(),
        locations = treeToValue(root.get("locations")) ?: error("locations is required"),
        _defaults = ServerGroupSpec(
          launchConfiguration = treeToValue(root.get("launchConfiguration")),
          capacity = treeToValue(root.get("capacity")),
          dependencies = treeToValue(root.get("dependencies")),
          health = treeToValue(root.get("health")),
          scaling = treeToValue(root.get("scaling")),
          tags = treeToValue(root.get("tags"))
        ),
        // this is pretty hairy but we can't just use treeToValue because the map's value type is erased
        overrides = (root.get("overrides") as? ObjectNode?)
          ?.fields()
          ?.asSequence()
          ?.associate { it.key to treeToValue<ServerGroupSpec>(it.value) }
          ?.filterNotNullValues()
          ?: emptyMap()
      )
    }

  private inline fun <reified T> DeserializationContext.treeToValue(node: TreeNode?): T? =
    if (node == null) null else parser.codec.treeToValue(node, T::class.java)

  @Suppress("UNCHECKED_CAST")
  private fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>
}
