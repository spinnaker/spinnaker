package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spinnaker")
class BaseUrlConfig {
  // placeholder values for tests
  var baseUrl: String = "https://spinnaker"
  var baseApiUrl: String = "https://spinnaker-api"
}
