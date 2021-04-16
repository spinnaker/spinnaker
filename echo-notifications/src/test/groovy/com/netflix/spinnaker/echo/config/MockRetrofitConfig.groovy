package com.netflix.spinnaker.echo.config

import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter
import retrofit.client.Client
import spock.lang.Specification

@Configuration
class MockRetrofitConfig extends Specification {
  @MockBean
  Client client

  @Bean
  RestAdapter.LogLevel getRetrofitLogLevel() {
    return RestAdapter.LogLevel.BASIC
  }
}
