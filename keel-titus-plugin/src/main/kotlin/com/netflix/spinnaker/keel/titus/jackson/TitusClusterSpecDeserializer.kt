package com.netflix.spinnaker.keel.titus.jackson

import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdNodeBasedDeserializer
import com.fasterxml.jackson.databind.node.ObjectNode
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.RedBlack
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.titus.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.TitusServerGroupSpec
import com.netflix.spinnaker.keel.api.toSimpleLocations
import com.netflix.spinnaker.keel.docker.ContainerProvider

class TitusClusterSpecDeserializer : StdNodeBasedDeserializer<TitusClusterSpec>(TitusClusterSpec::class.java) {
  override fun convert(root: JsonNode, context: DeserializationContext): TitusClusterSpec =
    with(context) {
      TitusClusterSpec(
        moniker = treeToValue<Moniker>(root.get("moniker")) ?: root.missingFieldError("moniker"),
        deployWith = treeToValue(root.get("deployWith")) ?: RedBlack(),
        locations = treeToValue<SimpleLocations>(root.get("locations"))
          ?: findInjectableLocations()
          ?: root.missingFieldError("locations"),
        _defaults = TitusServerGroupSpec(
          capacity = treeToValue(root.get("capacity")),
          constraints = treeToValue(root.get("constraints")),
          dependencies = treeToValue(root.get("dependencies")),
          env = treeToValue(root.get("env")),
          containerAttributes = treeToValue(root.get("containerAttributes")),
          resources = treeToValue(root.get("resources")),
          iamProfile = treeToValue(root.get("iamProfile")),
          entryPoint = treeToValue(root.get("entryPoint")),
          capacityGroup = treeToValue(root.get("capacityGroup")),
          migrationPolicy = treeToValue(root.get("migrationPolicy")),
          tags = treeToValue(root.get("tags"))
        ),
        // this is pretty hairy but we can't just use treeToValue because the map's value type is erased
        overrides = (root.get("overrides") as? ObjectNode?)
          ?.fields()
          ?.asSequence()
          ?.associate { it.key to treeToValue<TitusServerGroupSpec>(it.value) }
          ?.filterNotNullValues()
          ?: emptyMap(),
        container = treeToValue<ContainerProvider>(root.get("container"))
          ?: root.missingFieldError("container")
      )
    }

  private fun DeserializationContext.findInjectableLocations() =
    (findInjectableValue("locations", BeanProperty.Bogus(), null) as SubnetAwareLocations?)?.toSimpleLocations()

  private fun JsonNode.missingFieldError(fieldName: String): Nothing =
    throw JsonMappingException.wrapWithPath(IllegalStateException("$fieldName is required"), this, fieldName)

  private inline fun <reified T> DeserializationContext.treeToValue(node: TreeNode?): T? =
    if (node == null) null else parser.codec.treeToValue(node, T::class.java)

  @Suppress("UNCHECKED_CAST")
  private fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>
}
