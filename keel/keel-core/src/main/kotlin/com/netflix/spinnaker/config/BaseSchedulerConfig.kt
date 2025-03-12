package com.netflix.spinnaker.config

import java.time.Duration

/**
 * Base config class that defines common scheduler properties
 */
open class BaseSchedulerConfig {
  var minAgeDuration: Duration = Duration.ofMinutes(1)
  var batchSize: Int = 5
  var timeoutDuration: Duration = Duration.ofMinutes(2)
}
