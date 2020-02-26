package com.netflix.spinnaker.keel.apidocs

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import org.slf4j.LoggerFactory

/**
 * Extend this when you want to customize the schema for a model class regardless of whether you're
 * currently visiting a reference to it, or the schema itself. It works out schema refs, etc. and
 * just calls [customize] with the actual schema you can apply changes to.
 */
abstract class AbstractSchemaCustomizer : BaseModelConverter() {

  override fun resolve(
    annotatedType: AnnotatedType,
    context: ModelConverterContext,
    chain: MutableIterator<ModelConverter>
  ): Schema<*>? =
    super.resolve(annotatedType, context, chain)
      ?.also {
        val schema = if (annotatedType.isResolveAsRef) {
          val referencedSchema = context.definedModels[annotatedType.rawClass.simpleName]
          if (referencedSchema == null) {
            log.warn("Referenced schema ${annotatedType.rawClass.simpleName} not found")
            null
          } else {
            referencedSchema
          }
        } else {
          it
        }

        if (schema != null) {
          customize(schema, annotatedType.rawClass)
        }
      }

  /**
   * Provides a hook to customize [schema].
   *
   * @param type the model class the schema represents.
   * @param schema the current schema as generated so far.
   */
  abstract fun customize(schema: Schema<*>, type: Class<*>)

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
