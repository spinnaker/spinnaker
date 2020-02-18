package com.netflix.spinnaker.keel.apidocs

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.oas.models.media.Schema
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.springdoc.core.customizers.PropertyCustomizer
import org.springframework.stereotype.Component

@Component
class JavaTimePropertyCustomizer : PropertyCustomizer {

  private final val formattedTypes = mapOf(
    Duration::class.java to "duration",
    Instant::class.java to "date-time",
    ZonedDateTime::class.java to "date-time",
    OffsetDateTime::class.java to "date-time",
    LocalDateTime::class.java to "date-time",
    LocalDate::class.java to "date",
    LocalTime::class.java to "time"
  )

  override fun customize(property: Schema<*>?, type: AnnotatedType): Schema<*>? =
    property?.apply {
      val format = formattedTypes[type.baseType]
      if (format != null) {
        // OpenAPI ignores the return value so we have to just modify the schema in place.
        // See https://github.com/springdoc/springdoc-openapi/issues/441
        type("string")
        format(format)
        properties = emptyMap()
      }
    }
}
