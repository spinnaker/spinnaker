package com.netflix.spinnaker.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "slack")
@ConditionalOnProperty("slack.enabled'")
class SlackConfiguration {
  var token: String? = null
}
