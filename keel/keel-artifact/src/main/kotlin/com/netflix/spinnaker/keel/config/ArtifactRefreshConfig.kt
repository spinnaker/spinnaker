package com.netflix.spinnaker.keel.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keel.artifact-refresh")
class ArtifactRefreshConfig {
  var limit: Int = 1
}
