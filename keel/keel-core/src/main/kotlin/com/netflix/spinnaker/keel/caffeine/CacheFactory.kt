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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executor
import java.util.function.BiFunction
import java.util.function.Function

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
  fun <K : Any, V> asyncCache(
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
  fun <K : Any, V> asyncLoadingCache(
    cacheName: String,
    defaultMaximumSize: Long = 1000,
    defaultExpireAfterWrite: Duration = Duration.ofHours(1),
    dispatcher: CoroutineDispatcher = IO,
    loader: suspend (K) -> V?
  ): AsyncLoadingCache<K, V> =
    builder(cacheName, defaultMaximumSize, defaultExpireAfterWrite, dispatcher)
      .buildAsync(loader.toAsyncCacheLoader())
      .monitor(cacheName)

  /**
   * Builds an instrumented cache configured with values from [cacheProperties] or the supplied
   * defaults that uses [dispatcher] for computing values for the cache on a miss.
   *
   * Caches created using this method load all entries at once.
   */
  fun <K : Any, V> asyncBulkLoadingCache(
    cacheName: String,
    defaultMaximumSize: Long = 1000,
    defaultExpireAfterWrite: Duration = Duration.ofHours(1),
    dispatcher: CoroutineDispatcher = IO,
    loader: suspend () -> Map<K, V>
  ): AsyncLoadingCache<K, V> =
    builder(cacheName, defaultMaximumSize, defaultExpireAfterWrite, dispatcher)
      .buildAsync(loader.toAsyncBulkCacheLoader())
      .monitor(cacheName)
      .let { AsyncBulkLoadingCache(it) }

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

  private fun <K, V, C : AsyncCache<K, V>> C.monitor(cacheName: String) =
    CaffeineCacheMetrics.monitor(meterRegistry, this, cacheName)
}

fun <K : Any, V> (suspend (K) -> V?).toAsyncCacheLoader(): AsyncCacheLoader<K, V> =
  AsyncCacheLoader<K, V> { key, executor ->
    CoroutineScope(executor.asCoroutineDispatcher())
      .future { this@toAsyncCacheLoader.invoke(key) }
  }

fun <K : Any, V> (suspend () -> Map<K, V>).toAsyncBulkCacheLoader(): AsyncCacheLoader<K, V> =
  object : AsyncCacheLoader<K, V> {
    override fun asyncLoad(key: K, executor: Executor): CompletableFuture<V> {
      throw UnsupportedOperationException()
    }

    override fun asyncLoadAll(
      keys: Iterable<K>,
      executor: Executor
    ): CompletableFuture<Map<K, V>> =
      CoroutineScope(executor.asCoroutineDispatcher())
        .future { this@toAsyncBulkCacheLoader.invoke() }
  }

/**
 * An implementation of [AsyncLoadingCache] that _always_ uses [AsyncCacheLoader.asyncLoadAll] to
 * populate the cache.
 */
private class AsyncBulkLoadingCache<K : Any, V>(private val delegate: AsyncLoadingCache<K, V>) :
  AsyncLoadingCache<K, V> by delegate {
  override fun get(key: K): CompletableFuture<V> = getAll(listOf(key)).thenApply { it[key] }

  override fun get(key: K, mappingFunction: Function<in K, out V>): CompletableFuture<V> {
    throw UnsupportedOperationException()
  }

  override fun get(
    key: K,
    mappingFunction: BiFunction<in K, Executor, CompletableFuture<V>>
  ): CompletableFuture<V> {
    throw UnsupportedOperationException()
  }

  // The Kotlin compiler causes the default implementation of the method on the AsyncLoadingCache to be called
  // instead of the overridden function in the subclass (a BoundedLocalCache.BoundedLocalAsyncLoadingCache as
  // of this note). Explicitly calling the delegate here fixes that.
  // See https://youtrack.jetbrains.com/issue/KT-34612
  override fun asMap(): ConcurrentMap<K, CompletableFuture<V>> {
    return delegate.asMap()
  }
}

/**
 * A [CacheFactory] usable in tests that uses no-op metering and default configuration.
 */
val TEST_CACHE_FACTORY = CacheFactory(SimpleMeterRegistry(), CacheProperties())
