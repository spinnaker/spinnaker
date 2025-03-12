package com.netflix.spinnaker.jooq

import org.jooq.impl.AbstractConverter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class LocalDateTimeToInstantConverter : AbstractConverter<LocalDateTime, Instant>(
  LocalDateTime::class.java,
  Instant::class.java
) {
  override fun from(databaseObject: LocalDateTime?): Instant? =
    databaseObject?.toInstant(UTC)

  override fun to(userObject: Instant?): LocalDateTime? =
    userObject?.atZone(UTC)?.toLocalDateTime()
}
