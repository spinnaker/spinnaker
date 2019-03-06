package com.netflix.spinnaker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("sqs")
class SqsProperties {
  var queueARN: String = ""
}
