package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "keel.unhappy")
class UnhappyVetoConfig {
  var maxRetries: Int = 3
  var timeBetweenRetries: Duration = Duration.ofMinutes(30)
}
