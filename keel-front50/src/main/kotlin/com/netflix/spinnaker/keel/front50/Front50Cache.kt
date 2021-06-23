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
 * Memory-based cache for Front50 data. Primarily intended to avoid making repeated slow calls to Front50
 * to retrieve application config in bulk (see [allApplicationsCache]), but has the side-benefit that retrieving
 * individual application configs can be powered by the same bulk data.
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
    .asyncLoadingCache<String, Application>(cacheName = "applicationsByName") { app ->
      runCatching {
        front50Service.applicationByName(app)
      }.getOrElse { e ->
        throw CacheLoadingException("Error loading application $app into cache", e)
      }
    }

  private val allApplicationsCache: AsyncLoadingCache<String, Application> = cacheFactory
    .asyncBulkLoadingCache(cacheName = "allApplications") {
      runCatching {
        log.debug("Retrieving all applications from Front50 to populate cache")
        front50Service.allApplications(DEFAULT_SERVICE_ACCOUNT)
          .associateBy { it.name.toLowerCase() }
          .also {
            log.debug("Successfully primed application cache with ${it.size} entries")
          }
      }.getOrElse { e ->
        throw CacheLoadingException("Error loading allApplications cache", e)
      }
    }

  private val pipelinesByApplication: AsyncLoadingCache<String, List<Pipeline>> = cacheFactory
    .asyncLoadingCache(cacheName = "pipelinesByApplication") { app ->
      runCatching {
        front50Service.pipelinesByApplication(app)
      }.getOrElse { ex ->
        throw CacheLoadingException("Error loading pipelines for app $app", ex)
      }
    }

  /**
   * Returns the list of all currently known [Application] configs in Spinnaker from the cache.
   *
   * The first call to this method is expected to be as slow as a direct call to [Front50Service], as
   * the cache needs to be populated with a bulk call. Subsequent calls are expected to return
   * near-instantaneously until the cache expires.
   */
  suspend fun allApplications(): List<Application> =
    allApplicationsCache.asMap().values.map { it.await() }

  /**
   * Returns the cached [Application] by name.
   *
   * This call is expected to be slow before the cache is populated or refreshed, which should be a sporadic event.
   */
  suspend fun applicationByName(name: String): Application =
    applicationsByNameCache.get(name.toLowerCase()).await() ?: throw ApplicationNotFound(name)

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
          allApplicationsCache.put(it.name.toLowerCase(), CompletableFuture.supplyAsync { it })
          applicationsByNameCache.put(it.name.toLowerCase(), CompletableFuture.supplyAsync { it })
        }
        log.debug("Added ${apps.size} applications to the caches")
      } catch (e: Exception) {
        log.error("Error priming application caches: $e. Performance will be degraded.")
      }
    }
  }
}
