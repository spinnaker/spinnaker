package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.type.TypeBase
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.KClass
import org.springframework.stereotype.Component

/**
 * Handles any sealed type by defining its schema as `oneOf` the subtypes.
 */
@Component
class SealedClassModelConverter : BaseModelConverter() {
  override fun resolve(annotatedType: AnnotatedType, context: ModelConverterContext, chain: MutableIterator<ModelConverter>): Schema<*>? {
    val type = annotatedType.type
    return if (type is TypeBase && type.rawClass.kotlin.isSealed) {
      context.defineSchemaAsOneOf(
        type.rawClass,
        type.rawClass.kotlin.sealedSubclasses.filter { !it.isSealed }.map(KClass<*>::java)
      )
      ref(type.rawClass)
    } else {
      super.resolve(annotatedType, context, chain)
    }
  }
}
