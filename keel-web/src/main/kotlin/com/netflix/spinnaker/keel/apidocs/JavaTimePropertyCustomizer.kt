package com.netflix.spinnaker.keel.apidocs

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
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

  override fun customize(property: Schema<*>?, type: AnnotatedType): Schema<*>? {
    val format = formattedTypes[type.rawClass]
    return if (format != null) {
      StringSchema().format(format)
    } else {
      property
    }
  }
}
