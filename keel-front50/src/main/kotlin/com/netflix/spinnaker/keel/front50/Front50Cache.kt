package com.netflix.spinnaker.keel.front50

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.caffeine.CacheLoadingException
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.exceptions.ApplicationNotFound
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.Pipeline
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import javax.annotation.PostConstruct

/**
 * Memory-based cache for Front50 data.
 */
@Component
class Front50Cache(
  private val front50Service: Front50Service,
  private val cacheFactory: CacheFactory
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(Front50Cache::class.java) }
  }

  private val applicationsByNameCache: AsyncLoadingCache<String, Application> = cacheFactory
    .asyncLoadingCache(cacheName = "applicationsByName") { app ->
      runCatching {
        front50Service.applicationByName(app)
      }.getOrElse { e ->
        throw CacheLoadingException("applicationsByName", app, e)
      }
    }

  private val applicationsBySearchParamsCache: AsyncLoadingCache<Map<String, String>, List<Application>> = cacheFactory
    .asyncLoadingCache(cacheName = "applicationsBySearchParams") { searchParams ->
      runCatching {
        front50Service.searchApplications(searchParams)
      }.getOrElse { e ->
        throw CacheLoadingException("applicationsBySearchParams", searchParams, e)
      }
    }

  private val pipelinesByApplication: AsyncLoadingCache<String, List<Pipeline>> = cacheFactory
    .asyncLoadingCache(cacheName = "pipelinesByApplication") { app ->
      runCatching {
        front50Service.pipelinesByApplication(app)
      }.getOrElse { ex ->
        throw CacheLoadingException("pipelinesByApplication", app, ex)
      }
    }

  /**
   * @return the [Application] with the given name from the cache. This cache is primed during app startup using
   * the bulk API in Front50, and later updated/refreshed on an app-by-app basis.
   */
  suspend fun applicationByName(name: String): Application =
    applicationsByNameCache.get(name.toLowerCase()).await() ?: throw ApplicationNotFound(name)

  /**
   * @return the [Application]s matching the given search parameters from the cache.
   */
  suspend fun searchApplications(vararg searchParams: Pair<String, String>): List<Application> =
    applicationsBySearchParamsCache.get(searchParams.toMap()).await()

  suspend fun pipelinesByApplication(application: String): List<Pipeline> =
    pipelinesByApplication.get(application).await()

  @PostConstruct
  fun primeCaches() {
    log.debug("Priming Front50 application caches")
    runBlocking {
      try {
        val apps = front50Service.allApplications(DEFAULT_SERVICE_ACCOUNT)
        log.debug("Retrieved ${apps.size} applications from Front50")
        apps.forEach {
          applicationsByNameCache.put(it.name.toLowerCase(), CompletableFuture.supplyAsync { it })
        }
        log.debug("Added ${apps.size} applications to the cache")
      } catch (e: Exception) {
        log.error("Error priming application caches: $e. Performance will be degraded.")
      }
    }
  }
}
