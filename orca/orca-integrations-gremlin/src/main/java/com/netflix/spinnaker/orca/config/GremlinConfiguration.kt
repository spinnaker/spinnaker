package com.netflix.spinnaker.orca.config

import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.kork.client.ServiceClientProvider
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils
import com.netflix.spinnaker.orca.gremlin.GremlinService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper


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
      .rebuild<JsonMapper, JsonMapper.Builder>()
      .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
      .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS) // we want Instant serialized as ISO string
      .build()

    return serviceClientProvider.getService(
      GremlinService::class.java,
      DefaultServiceEndpoint("gremlin", RetrofitUtils.getBaseUrl(gremlinBaseUrl)),
      mapper
    )
  }
}
