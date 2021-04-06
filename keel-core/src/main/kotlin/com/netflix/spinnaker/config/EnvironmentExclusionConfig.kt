package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "keel.environment-exclusion")
class EnvironmentExclusionConfig {
  /**
   * The maximum duration of an environment exclusion lease.
   *
   * If a lease has expired, the enforcer assumes that the instance holding the lease has failed.
   */
  var leaseDuration : Duration  = Duration.ofSeconds(60)
}
