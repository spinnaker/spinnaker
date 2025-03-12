package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties("queue.pending-execution-service.sql")
class SqlPendingExecutionProperties {
  var enabled: Boolean = false
  var maxDepth: Int = 50
}
