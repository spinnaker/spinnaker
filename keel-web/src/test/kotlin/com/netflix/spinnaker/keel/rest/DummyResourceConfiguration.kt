package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.test.DummyResourceHandlerV1
import org.springframework.context.annotation.Bean

internal class DummyResourceConfiguration {
  @Bean
  fun dummyResourceHandler() = DummyResourceHandlerV1
}
