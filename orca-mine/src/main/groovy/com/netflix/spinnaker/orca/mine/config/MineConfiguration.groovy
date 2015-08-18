package com.netflix.spinnaker.orca.mine.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import([RetrofitConfiguration])
@ComponentScan([
  "com.netflix.spinnaker.orca.mine.pipeline",
  "com.netflix.spinnaker.orca.mine.tasks"
])
@ConditionalOnProperty(value = 'mine.baseUrl')
class MineConfiguration {

  @Autowired
  Client retrofitClient
  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Autowired
  ObjectMapper objectMapper

  @Bean
  Endpoint mineEndpoint(
    @Value('${mine.baseUrl}') String mineBaseUrl) {
    newFixedEndpoint(mineBaseUrl)
  }

  @Bean
  MineService mineService(Endpoint mineEndpoint) {
    new RestAdapter.Builder()
      .setEndpoint(mineEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(MineService))
      .setConverter(new JacksonConverter(objectMapper))
      .build()
      .create(MineService)
  }
}
