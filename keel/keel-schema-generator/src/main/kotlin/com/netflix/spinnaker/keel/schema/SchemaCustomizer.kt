package com.netflix.spinnaker.keel.schema

import kotlin.reflect.KClass
import kotlin.reflect.KType

interface SchemaCustomizer {
  fun supports(type: KClass<*>): Boolean

  fun buildSchema(): Schema
}
