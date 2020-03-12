package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.Locatable
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.full.isSubclassOf
import org.springframework.stereotype.Component

@Component
class LocatableModelConverter : BaseModelConverter() {
  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? {
    return super.resolve(annotatedType, context, chain)?.also { schema ->
      if (annotatedType.rawClass.kotlin.isSubclassOf(Locatable::class)) {
        schema.markOptional("locations")
      }
    }
  }
}
