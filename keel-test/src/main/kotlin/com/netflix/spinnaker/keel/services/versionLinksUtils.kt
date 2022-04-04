package com.netflix.spinnaker.keel.services

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.caffeine.CacheFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CompletableFuture
import java.util.function.Function

val scmData = mapOf(
  "stash" to "https://stash",
  "gitHub" to "https://github.com",
  "gitHubEnterprise" to "https://git.foo.com")

fun mockScmInfo(): ScmInfo {
  return mockk() {
    coEvery<Map<String, String?>> {
      getScmInfo()
    } answers {
      scmData
    }
  }
}

fun mockCacheFactory(): CacheFactory {
  val asyncLoadingCache: AsyncLoadingCache<Any, Map<String, String?>> = mockk {
    val cache = this
    every {
      get(any(), any() as Function<in Any, out Map<String, String?>>)
    } returns CompletableFuture.supplyAsync { scmData }
    coEvery {
      cache[any()]
    } returns CompletableFuture.supplyAsync { scmData }
  }

  return mockk() {
    every<AsyncLoadingCache<Any, Map<String, String?>>> {
      asyncLoadingCache<Any, Map<String, String?>>(any(), any(), any(), any(), any())
    } returns asyncLoadingCache
  }
}

