package com.netflix.spinnaker.keel.schema

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.plugins.SupportedKind
import kotlin.reflect.KClass

class ResourceKindSchemaCustomizer(
  private val kinds: Iterable<ResourceKind>
) : SchemaCustomizer {

  override fun supports(type: KClass<*>) = type == ResourceKind::class

  override fun buildSchema() = EnumSchema(
    description = "The resource kind that corresponds with each spec format.",
    enum = kinds.map(ResourceKind::toString)
  )
}
