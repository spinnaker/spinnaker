package com.netflix.spinnaker.keel.apidocs

import com.fasterxml.jackson.databind.type.SimpleType
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import org.springframework.stereotype.Component

/**
 * Kotlin-ized version of a class provided by [bnasslahsen](https://github.com/bnasslahsen).
 *
 * This ensures that any wildcard parameterized types are just handled as a raw type. That prevents
 * properties with that generic type being interpreted just as `Object` and instead as the upper
 * bound of the generic type.
 *
 * In our case this is essential so that [com.netflix.spinnaker.keel.api.Resource.spec] and
 * [com.netflix.spinnaker.keel.core.api.SubmittedResource.spec] have the correct type so we can
 * intercept it and add `oneOf` definitions.
 *
 * @see https://github.com/springdoc/springdoc-openapi/issues/434#issuecomment-586700760
 * @see ResourceSpecModelConverter
 */
@Component
class GenericWildcardTypeModelConverter : ModelConverter {
  override fun resolve(
    annotatedType: AnnotatedType,
    modelConverterContext: ModelConverterContext,
    iterator: Iterator<ModelConverter>
  ): Schema<*>? {
    var type = annotatedType.type
    if (type is SimpleType) {
      if (!type.bindings.isEmpty && Any::class.java == type.bindings.getBoundType(0).rawClass) {
        return resolve(
          AnnotatedType(type.rawClass)
            .jsonViewAnnotation(annotatedType.jsonViewAnnotation)
            .resolveAsRef(true),
          modelConverterContext,
          iterator
        )
      }
    } else if (type is ParameterizedType) {
      val parameterizedType = type as ParameterizedType
      val type = parameterizedType.actualTypeArguments[0]
      if (type is WildcardType) {
        if (Any::class.java == type.upperBounds[0]) {
          return resolve(
            AnnotatedType(parameterizedType.rawType)
              .jsonViewAnnotation(annotatedType.jsonViewAnnotation)
              .resolveAsRef(true),
            modelConverterContext,
            iterator
          )
        }
      }
    }
    return if (iterator.hasNext()) {
      iterator.next().resolve(annotatedType, modelConverterContext, iterator)
    } else {
      null
    }
  }
}
