package com.netflix.spinnaker.keel.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "keel.work-processing")
class WorkProcessingConfig {
  var timeoutDuration: Duration = Duration.ofMinutes(2)
  var artifactBatchSize: Int = 1
  var codeEventBatchSize: Int = 1
}
