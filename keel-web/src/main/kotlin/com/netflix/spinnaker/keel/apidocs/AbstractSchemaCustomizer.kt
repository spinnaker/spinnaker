package com.netflix.spinnaker.keel.apidocs

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.KProperty
import org.slf4j.LoggerFactory

/**
 * Extend this when you want to customize the schema for a model class regardless of whether you're
 * currently visiting a reference to it, or the schema itself. It works out schema refs, etc. and
 * just calls [customize] with the actual schema you can apply changes to.
 */
abstract class AbstractSchemaCustomizer() : BaseModelConverter() {

  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? =
    super.resolve(annotatedType, context, chain)
      ?.also {
        if (supports(annotatedType.rawClass)) {
          val schema = resolveIfReference(annotatedType, context, it)
          if (schema != null) {
            customize(schema, annotatedType.rawClass, context)
          }
        }
      }

  private fun resolveIfReference(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    schema: Schema<*>
  ): Schema<*>? =
    if (annotatedType.isResolveAsRef) {
      val referencedSchema = context.definedModels[annotatedType.rawClass.simpleName]
      if (referencedSchema == null) {
        log.warn("Referenced schema ${annotatedType.rawClass.simpleName} not found")
        null
      } else {
        referencedSchema
      }
    } else {
      schema
    }

  /**
   * Override this to only process certain classes. By default [customize] will run for all types.
   */
  open fun supports(type: Class<*>) = true

  /**
   * Provides a hook to customize [schema].
   *
   * @param type the model class the schema represents.
   * @param schema the current schema as generated so far.
   */
  abstract fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext)

  protected inline fun <reified T> Class<*>.isSubclassOf() = T::class.java.isAssignableFrom(this)

  /**
   * Invokes [callback] once for each of [properties], passing the name used for that property in
   * the API schema.
   */
  protected fun eachSchemaProperty(vararg properties: KProperty<*>, callback: (String) -> Unit) {
    properties.map { it.name }.forEach(callback)
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
