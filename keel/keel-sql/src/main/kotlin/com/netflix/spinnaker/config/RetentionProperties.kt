package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.temporal.ChronoUnit.DAYS

@ConfigurationProperties(prefix = "keel.retention")
data class RetentionProperties(
  val tasks: Duration = Duration.of(183, DAYS)
)
