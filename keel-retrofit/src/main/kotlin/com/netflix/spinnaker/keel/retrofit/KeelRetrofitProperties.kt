package com.netflix.spinnaker.keel.retrofit

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("retrofit")
class KeelRetrofitProperties {
  var spinnakerUser: String = "keel@spinnaker.io"
}
