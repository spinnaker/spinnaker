package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties


@ConfigurationProperties(prefix = "keel.artifact")
class ArtifactConfig() {
  var defaultMaxConsideredVersions: Int = 15
}
