package com.netflix.spinnaker.keel.redis.spring

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockEurekaConfig {
  @MockBean
  lateinit var eurekaClient: EurekaClient

  @Bean
  fun currentInstance(): InstanceInfo = InstanceInfo.Builder.newBuilder()
    .run {
      setAppName("keel")
      setASGName("keel-local")
      build()
    }
}
