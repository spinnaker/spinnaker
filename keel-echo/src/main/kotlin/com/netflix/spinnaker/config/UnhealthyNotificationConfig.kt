package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "keel.notifications.unhealthy")
class UnhealthyNotificationConfig(
  var enabled: Boolean = true,
  var minUnhealthyDuration: Duration = Duration.ofMinutes(10)
)
