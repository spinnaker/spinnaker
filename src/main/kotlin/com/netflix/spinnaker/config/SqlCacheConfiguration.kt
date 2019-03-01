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
import com.netflix.spinnaker.cats.sql.cache.SqlTableMetricsAgent
import com.netflix.spinnaker.clouddriver.cache.CustomSchedulableAgentIntervalProvider
import com.netflix.spinnaker.clouddriver.cache.EurekaStatusNodeStatusProvider
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.sql.SqlProvider
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.slf4j.MDCContext
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.lang.IllegalArgumentException
import java.time.Clock
import java.time.Duration
import java.util.Optional
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
@Configuration
@ConditionalOnProperty("sql.cache.enabled")
@Import(DefaultSqlConfiguration::class)
@EnableConfigurationProperties(SqlAgentProperties::class)
@ComponentScan("com.netflix.spinnaker.cats.sql.controllers")
class SqlCacheConfiguration {

  companion object {
    private val log = LoggerFactory.getLogger(SqlCacheConfiguration::class.java)
  }

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

  /**
   * sql.cache.async.poolSize: If set to a positive integer, a fixed thread pool of this size is created
   * as part of a coroutineContext. If sql.cache.maxQueryConcurrency is also >1 (default value: 4),
   * sql queries to fetch > 2 * sql.cache.readBatchSize cache keys will be made asynchronously in batches of
   * maxQueryConcurrency size.
   *
   * sql.tableNamespace: Name spaces data tables, as well as the agent lock table if using the SqlAgentScheduler.
   * Table namespacing allows flipping to new/empty data tables within the same master if necessary to rebuild
   * the cache from scratch, such as after disabling caching agents for an account/region.
   */
  @ObsoleteCoroutinesApi
  @Bean
  fun cacheFactory(jooq: DSLContext,
                   clock: Clock,
                   sqlProperties: SqlProperties,
                   cacheMetrics: SqlCacheMetrics,
                   dynamicConfigService: DynamicConfigService,
                   @Value("\${sql.cache.asyncPoolSize:0}") poolSize: Int,
                   @Value("\${sql.tableNamespace:#{null}}") tableNamespace: String?): NamedCacheFactory {
    if (tableNamespace != null && !tableNamespace.matches("""^\w+$""".toRegex())) {
      throw IllegalArgumentException("tableNamespace can only contain characters [a-z, A-Z, 0-9, _]")
    }

    /**
     * newFixedThreadPoolContext was marked obsolete in Oct 2018, to be reimplemented as a new
     * concurrency limiting threaded context factory with reduced context switch overhead. As of
     * Feb 2019, the new implementation is unreleased. See: https://github.com/Kotlin/kotlinx.coroutines/issues/261
     *
     * TODO: switch to newFixedThreadPoolContext's replacement when ready
     */
    val dispatcher = if (poolSize < 1) {
      null
    } else {
      newFixedThreadPoolContext(nThreads = poolSize, name = "catsSql") + MDCContext()
    }

    if (dispatcher != null) {
      log.info("Configured coroutine context with newFixedThreadPoolContext of $poolSize threads")
    }

    return SqlNamedCacheFactory(
      jooq,
      ObjectMapper(),
      dispatcher,
      clock,
      sqlProperties.retries,
      tableNamespace,
      cacheMetrics,
      dynamicConfigService
    )
  }

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
  @ConditionalOnExpression("\${sql.readOnly:false} == false")
  fun sqlTableMetricsAgent(jooq: DSLContext,
                           registry: Registry,
                           clock: Clock,
                           @Value("\${sql.tableNamespace:#{null}}") namespace: String?): SqlTableMetricsAgent =
    SqlTableMetricsAgent(jooq, registry, clock, namespace)

  @Bean
  @ConditionalOnExpression("\${sql.readOnly:false} == false")
  fun sqlAgentProvider(sqlTableMetricsAgent: SqlTableMetricsAgent): SqlProvider =
    SqlProvider(mutableListOf(sqlTableMetricsAgent))

  @Bean
  fun nodeStatusProvider(eurekaClient: Optional<EurekaClient>): NodeStatusProvider {
    return if (eurekaClient.isPresent) {
      EurekaStatusNodeStatusProvider(eurekaClient.get())
    } else {
      DefaultNodeStatusProvider()
    }
  }
}
