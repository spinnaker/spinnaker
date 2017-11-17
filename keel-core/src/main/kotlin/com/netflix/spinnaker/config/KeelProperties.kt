package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("keel")
class KeelProperties {
  var prettyPrintJson: Boolean = false
}
