package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.test.DummyResourceHandler
import org.springframework.context.annotation.Bean

internal class DummyResourceConfiguration {
  @Bean
  fun dummyResourceHandler() = DummyResourceHandler
}
