package com.netflix.spinnaker.kork.plugins.remote

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class RemotePluginsProviderTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("Gets a plugin by ID") {
      val result = subject.getById("netflix.remote1")
      expectThat(result).isA<RemotePlugin>()
        .get { result.id }.isEqualTo("netflix.remote1")
    }

    test("Gets by extension type") {
      val result = subject.getByExtensionType("stage")
      expectThat(result).isA<List<RemotePlugin>>()
        .get { result.size }.isEqualTo(2)
        .get { result.find { it.id == "netflix.remote3" } }.isNull()
    }
  }

  private class Fixture {
    val remotePluginsCache = setupRemotePluginsCache()
    val subject = RemotePluginsProvider(remotePluginsCache)

    private fun setupRemotePluginsCache(): RemotePluginsCache {
      val applicationEventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
      val remotePluginsCache = RemotePluginsCache(applicationEventPublisher)

      remotePluginsCache.put(
        RemotePlugin(
          "netflix.remote1",
          "0.0.1",
          setOf(
            RemoteExtension(
              "netflix.remote1.extension",
              "netflix.remote1",
              "stage",
              mutableMapOf(),
              mockk(relaxed = true)
            )
          )
        )
      )

      remotePluginsCache.put(
        RemotePlugin(
          "netflix.remote2",
          "0.0.1",
          setOf(
            RemoteExtension(
              "netflix.remote2.extension",
              "netflix.remote1",
              "stage",
              mutableMapOf(),
              mockk(relaxed = true)
            )
          )
        )
      )

      remotePluginsCache.put(
        RemotePlugin(
          "netflix.remote3",
          "0.0.1",
          setOf(
            RemoteExtension(
              "netflix.remote3.extension",
              "netflix.remote1",
              "resourceHandler",
              mutableMapOf(),
              mockk(relaxed = true)
            )
          )
        )
      )

      return remotePluginsCache
    }
  }
}
