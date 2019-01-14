package com.netflix.spinnaker.cats.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.WriteableCache
import com.netflix.spinnaker.cats.mem.InMemoryCache
import com.netflix.spinnaker.cats.provider.DefaultProviderCache
import com.netflix.spinnaker.cats.provider.ProviderCacheSpec
import com.netflix.spinnaker.cats.sql.cache.SpectatorSqlCacheMetrics
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initDatabase

class SqlProviderCacheSpec extends ProviderCacheSpec {

  @Shared
  @AutoCleanup("close")
  SqlTestUtil.TestDatabase currentDatabase

  WriteableCache backingStore

  def setup() {
    (backingStore as SqlCache).clearCreatedTables()
    return initDatabase("jdbc:h2:mem:test")
  }


  def cleanup() {
    currentDatabase.context.dropSchemaIfExists("test")
  }

  @Override
  SqlProviderCache getDefaultProviderCache() {
    getCache() as SqlProviderCache
  }


  @Override
  Cache getSubject() {
    def mapper = new ObjectMapper()
    def clock = new Clock.FixedClock(Instant.EPOCH, ZoneId.of("UTC"))
    def sqlRetryProperties = new SqlRetryProperties()
    def sqlMetrics = new SpectatorSqlCacheMetrics(new NoopRegistry())
    currentDatabase = initDatabase()
    backingStore = new SqlCache(
      "test",
      currentDatabase.context,
      mapper,
      clock,
      sqlRetryProperties,
      "test",
      sqlMetrics,
      10,
      10
    )

    return new SqlProviderCache(backingStore)
  }

}
