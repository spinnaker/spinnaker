package com.netflix.spinnaker.cats.sql.cache

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.module.CatsModule
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.cats.sql.SqlProviderCache
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import sun.awt.AppContext
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class SqlCleanupStaleOnDemandCachesAgent(
  private val applicationContext: ApplicationContext,
  private val registry: Registry,
  private val clock: Clock
) : RunnableAgent, CustomScheduledAgent {

  companion object {
    private val DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(20)
    private val DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3)
    private val MAX_ONDEMAND_AGE_MILLIS = TimeUnit.MINUTES.toMillis(30)

    private val log = LoggerFactory.getLogger(SqlCleanupStaleOnDemandCachesAgent::class.java)
  }

  private val countId = registry.createId("cats.sqlCache.cleanedStaleOnDemandKeys.count")
  private val timeId = registry.createId("cats.sqlCache.cleanedStaleOnDemandKeys.time")

  override fun run() {
    val start = clock.millis()

    val deleted = getCache().cleanOnDemand(MAX_ONDEMAND_AGE_MILLIS)

    registry.gauge(countId).set(deleted.toDouble())
    registry.gauge(timeId).set((clock.millis() - start).toDouble())
  }

  private fun getCache(): SqlProviderCache {
    return applicationContext.getBean(CatsModule::class.java)
      .providerRegistry
      .providerCaches
      .first() as SqlProviderCache
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis(): Long = DEFAULT_POLL_INTERVAL_MILLIS
  override fun getTimeoutMillis(): Long = DEFAULT_TIMEOUT_MILLIS
}
