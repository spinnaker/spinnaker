package com.netflix.spinnaker.cats.sql.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import org.jooq.DSLContext
import java.time.Clock

class SqlNamedCacheFactory(
  private val jooq: DSLContext,
  private val mapper: ObjectMapper,
  private val clock: Clock,
  private val sqlRetryProperties: SqlRetryProperties,
  private val prefix: String?,
  private val cacheMetrics: SqlCacheMetrics,
  private val dynamicConfigService: DynamicConfigService
) : NamedCacheFactory {

  override fun getCache(name: String): WriteableCache {
    return SqlCache(name, jooq, mapper, clock, sqlRetryProperties, prefix, cacheMetrics, dynamicConfigService)
  }
}
