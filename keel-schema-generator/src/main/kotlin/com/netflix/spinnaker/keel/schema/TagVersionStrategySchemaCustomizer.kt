package com.netflix.spinnaker.keel.schema

import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import kotlin.reflect.KClass

object TagVersionStrategySchemaCustomizer : SchemaCustomizer {
  override fun supports(type: KClass<*>): Boolean = type == TagVersionStrategy::class

  override fun buildSchema(): Schema = EnumSchema(
    enum = TagVersionStrategy.values().map { it.friendlyName },
    description = "The strategy used to parse a version from the tag"
  )
}
