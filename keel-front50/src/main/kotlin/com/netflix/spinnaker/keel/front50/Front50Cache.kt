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

@Component
/**
 * Memory-based cache for Front50 data. Primarily intended to avoid making repeated slow calls to Front50
 * to retrieve application config in bulk (see [allApplications]), but has the side-benefit that retrieving
 * individual application configs can be powered by the same bulk data.
 */
class Front50Cache(
  private val front50Service: Front50Service,
  private val cacheFactory: CacheFactory
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(Front50Cache::class.java) }
  }

  private val applicationsByName: AsyncLoadingCache<String, Application> = cacheFactory
    .asyncBulkLoadingCache(cacheName = "applicationsByName") {
      runCatching {
        log.debug("Retrieving all applications from Front50")
        front50Service.allApplications(DEFAULT_SERVICE_ACCOUNT)
          .associateBy { it.name.toLowerCase() }
      }
        .getOrElse { ex ->
          throw CacheLoadingException("Error loading applicationsByName cache", ex)
        }
    }.also {
      // force the cache to initialize
      it.get("dummy")
    }

  private val pipelinesByApplication: AsyncLoadingCache<String, List<Pipeline>> = cacheFactory
    .asyncLoadingCache(cacheName = "pipelinesByApplication") { app ->
      runCatching {
        front50Service.pipelinesByApplication(app)
      }
        .getOrElse { ex ->
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
    applicationsByName.asMap().values.map { it.await() }

  /**
   * Returns the cached [Application] by name.
   *
   * This call is expected to be slow before the cache is populated or refreshed, which should be a sporadic event.
   */
  suspend fun applicationByName(name: String): Application =
    applicationsByName.get(name.toLowerCase()).await() ?: throw ApplicationNotFound(name)

  suspend fun pipelinesByApplication(application: String): List<Pipeline> =
    pipelinesByApplication.get(application).await()
}
