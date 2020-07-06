package com.netflix.spinnaker.keel.apidocs

import com.netflix.spinnaker.keel.api.docs.Description
import com.netflix.spinnaker.keel.api.docs.Literal
import com.netflix.spinnaker.keel.api.docs.Optional
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered.LOWEST_PRECEDENCE
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(LOWEST_PRECEDENCE) // needs to run after KotlinOptionalPropertyConverter
class ApiAnnotationModelConverter : AbstractSchemaCustomizer() {

  override fun customize(schema: Schema<*>, type: Class<*>, context: ModelConverterContext) {
    handleClassAnnotations(type, schema)

    schema.properties?.forEach { (name, propertySchema) ->
      val property = type.kotlin.memberProperties.find { it.name == name }
      if (property == null) {
        log.warn("No property $name found for ${type.kotlin.qualifiedName}")
      } else {
        handlePropertyAnnotations(property, schema, propertySchema)
      }
    }
  }

  private fun handleClassAnnotations(type: Class<*>, schema: Schema<*>) {
    val literal = type.kotlin.findAnnotation<Literal>()
    if (literal != null) {
      schema.type(literal.type)
      schema.enum = listOf(literal.value)
    }

    val description = type.kotlin.findAnnotation<Description>()
    if (description != null) {
      schema.description(description.value)
    }
  }

  private fun handlePropertyAnnotations(
    property: KProperty1<*, *>,
    schema: Schema<*>,
    propertySchema: Schema<*>
  ) {
    val description = property.findAnnotation<Description>()
    if (description != null) {
      if (propertySchema.`$ref` == null) {
        // if the property schema is not a reference to a model we can add things directly
        propertySchema.description(description.value)
      } else {
        // it's not valid to have anything other than $ref on a schema if the $ref is non-null. To
        // add additional things you use `allOf` where the $ref schema is one of the elements and
        // any additional stuff is on another element. For example, `SubmittedResource.spec` might
        // look like this:
        //
        //     spec:
        //       nullable: false
        //       allOf:
        //       - $ref: '#/components/schemas/ResourceSpec'
        //       - description: The specification of the resource
        schema.properties[property.name] = ComposedSchema()
          .addAllOfItem(propertySchema)
          .addAllOfItem(Schema<Any>().description(description.value))
      }
    }

    if (property.findAnnotation<Optional>() != null) {
      schema.markOptional(property.name)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
