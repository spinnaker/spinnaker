package com.netflix.spinnaker.keel.constraints

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("defaults.constraint.canary")
class CanaryConstraintConfigurationProperties {
  var metricsAccount: String = "atlas-global.prod"
  var storageAccount: String = "s3-objects"
}
