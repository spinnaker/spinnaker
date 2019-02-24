package com.netflix.spinnaker.cats.sql.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import org.jooq.DSLContext
import java.time.Clock
import kotlin.contracts.ExperimentalContracts
import kotlin.coroutines.CoroutineContext

class SqlNamedCacheFactory(
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val dispatcher: CoroutineContext?,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties,
  private val prefix: String?,
  private val cacheMetrics: SqlCacheMetrics,
  private val dynamicConfigService: DynamicConfigService
) : NamedCacheFactory {

  @ExperimentalContracts
  override fun getCache(name: String): WriteableCache {
    return SqlCache(
      name,
      jooq,
      mapper,
      dispatcher,
      clock,
      sqlRetryProperties,
      prefix,
      cacheMetrics,
      dynamicConfigService
    )
  }
}
