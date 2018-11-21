package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.Provider
import com.netflix.spinnaker.cats.sql.SqlProviderRegistry
import com.netflix.spinnaker.cats.sql.cache.SqlNamedCacheFactory
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import org.jooq.DSLContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import org.springframework.beans.factory.annotation.Value
import java.time.Clock

@Configuration
@ConditionalOnProperty("sql.cache.enabled")
@Import(DefaultSqlConfiguration::class)
class SqlCacheConfiguration {

  @Bean
  fun catsModule(providers: List<Provider>,
                 executionInstrumentation: List<ExecutionInstrumentation>,
                 cacheFactory: NamedCacheFactory,
                 agentScheduler: AgentScheduler<*>) : CatsModule {
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
                   @Value("\${sql.cache.batchSize:1000}") batchSize: Int): NamedCacheFactory =
    SqlNamedCacheFactory(jooq, ObjectMapper(), clock, sqlProperties.retries, batchSize)

  @Bean
  fun coreProvider(): CoreProvider = CoreProvider(emptyList())
}
