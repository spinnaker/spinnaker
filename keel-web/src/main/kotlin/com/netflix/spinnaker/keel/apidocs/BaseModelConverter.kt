package com.netflix.spinnaker.keel.apidocs

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.RefUtils.constructRef
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema

abstract class BaseModelConverter : ModelConverter {

  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? =
    if (chain.hasNext()) {
      chain.next().resolve(annotatedType, context, chain)
    } else {
      null
    }

  /**
   * @return a reference to the schema for [T] (which must already have been defined).
   */
  protected inline fun <reified T> ref(): Schema<*> =
    ref(T::class.java)

  /**
   * @return a reference to the schema for [type] (which must already have been defined).
   */
  protected fun ref(type: Class<*>): Schema<*> =
    Schema<Any>().`$ref`(constructRef(type.simpleName))

  /**
   * Defines the schema for [type] as `oneOf` the schemas for [subTypes].
   */
  protected fun ModelConverterContext.defineSchemaAsOneOf(type: Class<*>, subTypes: List<Class<*>>) {
    subTypes.forEach {
      if (!definedModels.containsKey(it.simpleName)) {
        defineModel(it.simpleName, resolve(AnnotatedType(it)))
      }
    }

    if (!definedModels.containsKey(type.simpleName)) {
      defineModel(
        type.simpleName,
        ComposedSchema()
          .apply {
            subTypes.forEach {
              addOneOfItem(ref(it))
            }
          }
      )
    }
  }
}
