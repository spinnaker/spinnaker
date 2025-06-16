package com.netflix.spinnaker.orca.config

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import com.netflix.spinnaker.orca.gremlin.GremlinService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration


@Configuration
@ComponentScan(
  "com.netflix.spinnaker.orca.gremlin.pipeline",
  "com.netflix.spinnaker.orca.gremlin.tasks"
)
class GremlinConfiguration {

  @Bean
  fun gremlinService(
    @Value("\${integrations.gremlin.base-url:https://api.gremlin.com/v1}") gremlinBaseUrl: String,
    serviceClientProvider: ServiceClientProvider
  ): GremlinService {
    val mapper = OrcaObjectMapper
      .newInstance()
      .setPropertyNamingStrategy(
        PropertyNamingStrategy.SNAKE_CASE
      )
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // we want Instant serialized as ISO string

    return serviceClientProvider.getService(
      GremlinService::class.java,
      DefaultServiceEndpoint("gremlin", RetrofitUtils.getBaseUrl(gremlinBaseUrl)),
      mapper
    )
  }
}
