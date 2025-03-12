package com.netflix.spinnaker.keel.jackson

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonCreator.Mode.PROPERTIES
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.Annotated
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector
import com.netflix.spinnaker.keel.api.schema.Factory

/**
 * Supports the use of the [Factory] annotation.
 */
class FactoryAnnotationIntrospector : NopAnnotationIntrospector() {
  override fun findCreatorAnnotation(config: MapperConfig<*>, a: Annotated): JsonCreator.Mode? =
    when {
      // if this constructor has @Factory annotation, use it to create the object
      a.hasAnnotation(Factory::class.java) -> PROPERTIES
      // if *any other* constructor has @Factory annotation, ignore this one
      a.rawType.constructors.any { it.isAnnotationPresent(Factory::class.java) } -> DISABLED
      // in any other case fall back to the default
      else -> super.findCreatorAnnotation(config, a)
    }
}
