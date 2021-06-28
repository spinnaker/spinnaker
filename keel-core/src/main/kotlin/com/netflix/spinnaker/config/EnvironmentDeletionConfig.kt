package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keel.environment-deletion")
class EnvironmentDeletionConfig {
  var enabled: Boolean = true
  var check: BaseSchedulerConfig = BaseSchedulerConfig()
}
