package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.discovery.EurekaClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultNodeStatusProvider
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.sql.SqlProviderRegistry
import com.netflix.spinnaker.cats.sql.cache.SpectatorSqlCacheMetrics
import com.netflix.spinnaker.cats.sql.cache.SqlCacheMetrics
import com.netflix.spinnaker.cats.sql.cache.SqlNamedCacheFactory
import com.netflix.spinnaker.clouddriver.cache.CustomSchedulableAgentIntervalProvider
import com.netflix.spinnaker.clouddriver.cache.EurekaStatusNodeStatusProvider
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Duration
import java.util.Optional

@Configuration
@ConditionalOnProperty("sql.cache.enabled")
@Import(DefaultSqlConfiguration::class)
@EnableConfigurationProperties(SqlAgentProperties::class)
class SqlCacheConfiguration {

  @Bean
  fun sqlCacheMetrics(registry: Registry): SqlCacheMetrics {
    return SpectatorSqlCacheMetrics(registry)
  }

  @Bean
  fun catsModule(providers: List<Provider>,
                 executionInstrumentation: List<ExecutionInstrumentation>,
                 cacheFactory: NamedCacheFactory,
                 agentScheduler: AgentScheduler<*>): CatsModule {
    return CatsModule.Builder()
      .providerRegistry(SqlProviderRegistry(providers, cacheFactory))
      .cacheFactory(cacheFactory)
      .scheduler(agentScheduler)
      .instrumentation(executionInstrumentation)
      .build(providers)
  }

  @Bean
  fun cacheFactory(jooq: DSLContext,
                   clock: Clock,
                   sqlProperties: SqlProperties,
                   cacheMetrics: SqlCacheMetrics,
                   dynamicConfigService: DynamicConfigService,
                   @Value("\${sql.tableNamespace:#{null}}") tableNamespace: String?): NamedCacheFactory =
    SqlNamedCacheFactory(
      jooq,
      ObjectMapper(),
      clock,
      sqlProperties.retries,
      tableNamespace,
      cacheMetrics,
      dynamicConfigService
    )

  @Bean
  fun coreProvider(): CoreProvider = CoreProvider(emptyList())

  @Bean
  fun agentIntervalProvider(sqlAgentProperties: SqlAgentProperties): AgentIntervalProvider {
    return CustomSchedulableAgentIntervalProvider(
      Duration.ofSeconds(sqlAgentProperties.poll.intervalSeconds).toMillis(),
      Duration.ofSeconds(sqlAgentProperties.poll.errorIntervalSeconds).toMillis(),
      Duration.ofSeconds(sqlAgentProperties.poll.timeoutSeconds).toMillis()
    )
  }

  @Bean
  fun nodeStatusProvider(eurekaClient: Optional<EurekaClient>): NodeStatusProvider {
    return if (eurekaClient.isPresent) {
      EurekaStatusNodeStatusProvider(eurekaClient.get())
    } else {
      DefaultNodeStatusProvider()
    }
  }
}
