package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("keel.dns")
class DnsConfig(
  var defaultDomain: String = "keel.io"
)
