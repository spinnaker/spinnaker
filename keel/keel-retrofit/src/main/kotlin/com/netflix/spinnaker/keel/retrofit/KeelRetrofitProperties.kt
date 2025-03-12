package com.netflix.spinnaker.keel.retrofit

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("retrofit")
class KeelRetrofitProperties {
  var spinnakerUser: String = "keel@spinnaker.io"

  var userAgent: String = "Spinnaker-$appName/$appVersion"

  private val appName: String
    get() = System.getProperty("spring.application.name", "unknown")

  private val appVersion: String
    get() = javaClass.`package`.implementationVersion ?: "1.0"
}
