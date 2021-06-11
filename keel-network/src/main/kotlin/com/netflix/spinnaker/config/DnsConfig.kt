package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("keel.dns")
data class DnsConfig(
  val defaultDomain: String
)