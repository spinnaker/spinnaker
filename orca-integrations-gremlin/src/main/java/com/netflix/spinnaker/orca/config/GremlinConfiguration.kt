package com.netflix.spinnaker.orca.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.orca.gremlin.GremlinConverter
import com.netflix.spinnaker.orca.gremlin.GremlinService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.Client

@Configuration
@ConditionalOnProperty("integrations.gremlin.enabled")
@ComponentScan(
  "com.netflix.spinnaker.orca.gremlin.pipeline",
  "com.netflix.spinnaker.orca.gremlin.tasks"
)
class GremlinConfiguration {

  @Bean
  fun gremlinEndpoint(
    @Value("\${integrations.gremlin.baseUrl}") gremlinBaseUrl: String): Endpoint {
    return Endpoints.newFixedEndpoint(gremlinBaseUrl)
  }

  @Bean
  fun gremlinService(
    retrofitClient: Client,
    gremlinEndpoint: Endpoint,
    spinnakerRequestInterceptor: RequestInterceptor
  ): GremlinService {
    val mapper = OrcaObjectMapper
      .newInstance()
      .setPropertyNamingStrategy(
        PropertyNamingStrategy.SNAKE_CASE)
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // we want Instant serialized as ISO string
    return RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(gremlinEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(RestAdapter.LogLevel.BASIC)
      .setLog(RetrofitSlf4jLog(GremlinService::class.java))
      .setConverter(GremlinConverter(mapper))
      .build()
      .create(GremlinService::class.java)
  }
}
