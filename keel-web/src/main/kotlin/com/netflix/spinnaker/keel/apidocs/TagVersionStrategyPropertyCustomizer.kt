package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.oas.models.media.Schema
import org.springdoc.core.customizers.PropertyCustomizer
import org.springframework.stereotype.Component

@Component
class TagVersionStrategyPropertyCustomizer : PropertyCustomizer {
  override fun customize(property: Schema<*>?, type: AnnotatedType): Schema<*>? {
    if (type.rawClass == TagVersionStrategy::class.java) {
      (property as? Schema<String>)?.enum = TagVersionStrategy.values().map { it.friendlyName }
    }
    return property
  }
}
