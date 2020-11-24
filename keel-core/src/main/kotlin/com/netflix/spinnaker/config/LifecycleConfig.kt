package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "keel.lifecycle-monitor")
class LifecycleConfig {
  var minAgeDuration: Duration = Duration.ofSeconds(30)
  var batchSize: Int = 1
  var timeoutDuration: Duration = Duration.ofMinutes(2)
  var numFailuresAllowed: Int = 5
}

