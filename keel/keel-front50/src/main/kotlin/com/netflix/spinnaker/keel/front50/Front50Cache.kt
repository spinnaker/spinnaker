package com.netflix.spinnaker.keel.front50

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.caffeine.CacheLoadingException
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.exceptions.ApplicationNotFound
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.front50.model.GitRepository
import com.netflix.spinnaker.keel.front50.model.ManagedDeliveryConfig
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
   * if [invalidateCache] is true it will first invalidate the cache for the app name.
   * This is useful when users update their git repo details.
   */
  suspend fun applicationByName(name: String, invalidateCache: Boolean = false): Application {
    if (invalidateCache) {
      invalidateApplicationByNameCache(name)
    }
    return applicationsByNameCache.get(name.toLowerCase()).await() ?: throw ApplicationNotFound(name)
  }

  /**
   * @return the [Application]s matching the given search parameters from the cache.
   */
  suspend fun searchApplications(searchParams: Map<String, String>): List<Application> =
    applicationsBySearchParamsCache.get(searchParams).await()

  suspend fun searchApplicationsByRepo(repo: GitRepository): List<Application> =
    searchApplications(repo.toSearchParams())

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

  private fun updateApplicationByName(app: Application) {
    applicationsByNameCache.put(app.name.toLowerCase(), CompletableFuture.supplyAsync { app })
  }

  private fun invalidateApplicationByNameCache(app: String) {
    applicationsByNameCache.synchronous().invalidate(app.toLowerCase())
  }

  private fun invalidateSearchParamsCache(app: Application) {
    with(app) {
      // We invalidate the cache, as it's easier than updating it
      if (repoType != null && repoProjectKey != null && repoSlug != null) {
        applicationsBySearchParamsCache.synchronous()
          .invalidate(GitRepository(repoType, repoProjectKey, repoSlug).toSearchParams())
      }
    }
  }

  suspend fun updateManagedDeliveryConfig(application: String, user: String, settings: ManagedDeliveryConfig): Application {
    val front50App = applicationByName(application)
    return updateManagedDeliveryConfig(front50App, user, settings)
  }

  suspend fun updateManagedDeliveryConfig(application: Application, user: String, settings: ManagedDeliveryConfig): Application {
    val front50App = front50Service.updateApplication(
      application.name,
      user,
      Application(
        name = application.name,
        email = application.email,
        managedDelivery = settings
      )
    )
    updateApplicationByName(front50App)
    invalidateSearchParamsCache(front50App)
    return front50App
  }

  private fun GitRepository.toSearchParams() = mapOf(
    "repoType" to type,
    "repoProjectKey" to project,
    "repoSlug" to slug
  )
}
