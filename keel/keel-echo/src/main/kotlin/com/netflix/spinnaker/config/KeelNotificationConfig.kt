package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keel.notifications.interactive-notifications")
class KeelNotificationConfig {
  var enabled: Boolean = false
}