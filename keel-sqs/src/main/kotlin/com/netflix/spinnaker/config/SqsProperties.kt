package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("sqs")
class SqsProperties {
  var queueARN: String = ""
  var visibilityTimeoutSeconds: Int = 60
  var waitTimeSeconds: Int = 10
  var listenerFibers: Int = 10
}
