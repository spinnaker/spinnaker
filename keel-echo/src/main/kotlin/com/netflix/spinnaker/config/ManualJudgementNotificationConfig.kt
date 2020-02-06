package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keel.constraints.manual-judgement.interactive-notifications")
class ManualJudgementNotificationConfig {
  var enabled: Boolean = false
}
