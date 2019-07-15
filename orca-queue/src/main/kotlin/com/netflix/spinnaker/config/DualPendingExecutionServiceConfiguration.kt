package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("queue.pending-execution-service.dual")
class DualPendingExecutionServiceConfiguration {
  var enabled: Boolean = false
  var primaryClass: String? = null
  var previousClass: String? = null
}
