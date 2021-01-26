package com.netflix.spinnaker.keel.caffeine

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.future.future
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@EnableConfigurationProperties(CacheProperties::class)
class CacheFactory(
  private val meterRegistry: MeterRegistry,
  private val cacheProperties: CacheProperties
) {
  /**
   * Builds an instrumented cache configured with values from [cacheProperties] or the supplied
   * defaults that uses [dispatcher] for computing values for the cache on a miss.
   */
  fun <K, V> asyncCache(
    cacheName: String,
    defaultMaximumSize: Long = 1000,
    defaultExpireAfterWrite: Duration = Duration.ofHours(1),
    dispatcher: CoroutineDispatcher = IO
  ): AsyncCache<K, V> =
    builder(cacheName, defaultMaximumSize, defaultExpireAfterWrite, dispatcher)
      .buildAsync<K, V>()
      .monitor(cacheName)

  /**
   * Builds an instrumented cache configured with values from [cacheProperties] or the supplied
   * defaults that uses [dispatcher] for computing values for the cache on a miss.
   */
  fun <K, V> asyncLoadingCache(
    cacheName: String,
    defaultMaximumSize: Long = 1000,
    defaultExpireAfterWrite: Duration = Duration.ofHours(1),
    dispatcher: CoroutineDispatcher = IO,
    loader: suspend (K) -> V?
  ): AsyncLoadingCache<K, V> =
    builder(cacheName, defaultMaximumSize, defaultExpireAfterWrite, dispatcher)
      .buildAsync(loader.toAsyncCacheLoader())
      .monitor(cacheName)

  private fun builder(
    cacheName: String,
    defaultMaximumSize: Long,
    defaultExpireAfterWrite: Duration,
    dispatcher: CoroutineDispatcher
  ): Caffeine<Any, Any> =
    cacheProperties.caches[cacheName].let { settings ->
      Caffeine.newBuilder()
        .executor(dispatcher.asExecutor())
        .maximumSize(settings?.maximumSize ?: defaultMaximumSize)
        .expireAfterWrite(settings?.expireAfterWrite ?: defaultExpireAfterWrite)
        .recordStats()
    }

  private fun <K, V, C: AsyncCache<K, V>> C.monitor(cacheName: String) =
    CaffeineCacheMetrics.monitor(meterRegistry, this, cacheName)
}

fun <K, V> (suspend (K) -> V?).toAsyncCacheLoader() : AsyncCacheLoader<K, V> =
  AsyncCacheLoader<K, V> { key, executor ->
    CoroutineScope(executor.asCoroutineDispatcher())
      .future { this@toAsyncCacheLoader.invoke(key) }
  }

/**
 * A [CacheFactory] usable in tests that uses no-op metering and default configuration.
 */
val TEST_CACHE_FACTORY = CacheFactory(SimpleMeterRegistry(), CacheProperties())
