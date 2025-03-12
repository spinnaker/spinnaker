package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keel.environment-deletion")
class EnvironmentDeletionConfig {
  companion object {
    const val DEFAULT_MAX_RESOURCE_DELETION_ATTEMPTS = 5
  }

  var enabled: Boolean = true
  var dryRun: Boolean = false
  var check: BaseSchedulerConfig = BaseSchedulerConfig()
  var maxResourceDeletionAttempts: Int = DEFAULT_MAX_RESOURCE_DELETION_ATTEMPTS
}
