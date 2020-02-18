package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.type.TypeBase
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema

/**
 * Base for model converters that define a [Schema] for a base type as `oneOf` a list of [subTypes].
 */
abstract class SubtypesModelConverter<T>(
  private val baseType: Class<T>
) : BaseModelConverter() {

  protected abstract val subTypes: List<Class<out T>>

  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? {
    val type = annotatedType.type
    return if (type is TypeBase && type.rawClass == baseType) {
      context.defineSchemaAsOneOf(baseType, subTypes)
      ref(baseType)
    } else {
      super.resolve(annotatedType, context, chain)
    }
  }
}
