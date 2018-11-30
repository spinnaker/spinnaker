package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("keel.k8s")
data class KubernetesConfigurationProperties(
  var connectTimeoutSeconds: Long = 3,
  var readTimeoutSeconds: Long = 60
)
