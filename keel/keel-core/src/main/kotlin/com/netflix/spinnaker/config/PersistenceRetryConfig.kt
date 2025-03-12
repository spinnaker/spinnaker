package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "keel.persistence.retries")
class PersistenceRetryConfig {
  var reads = BasePersistenceRetryConfig()
  var writes = BasePersistenceRetryConfig()
}
