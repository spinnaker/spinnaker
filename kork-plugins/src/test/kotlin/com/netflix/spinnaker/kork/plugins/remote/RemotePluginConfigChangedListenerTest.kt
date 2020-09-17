package com.netflix.spinnaker.kork.plugins.remote

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.kork.api.plugins.remote.RemoteExtensionConfig
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
import com.netflix.spinnaker.kork.plugins.events.RemotePluginConfigChanged
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPointConfig
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPointDefinition
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.ObjectProvider
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class RemotePluginConfigChangedListenerTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    test("Enabled event instantiates remote plugin object and caches it") {
      subject.onApplicationEvent(enableEvent)
      val result = remotePluginsCache.get(enableEvent.pluginId)
      expectThat(result).isA<RemotePlugin>()
        .get { result?.id }.isEqualTo("netflix.remote1")
    }

    test("Updated event replaces previously enabled plugin") {
      subject.onApplicationEvent(enableEvent)
      subject.onApplicationEvent(updateEvent)
      val result = remotePluginsCache.getAll()
      expectThat(result.values).isA<MutableCollection<RemotePlugin>>()
        .get { result.values.size }.isEqualTo(1)
        .get { result.values.first().id }.isEqualTo("netflix.remote1")
    }

    test("Disabled event removes previously enabled plugin") {
      subject.onApplicationEvent(enableEvent)
      subject.onApplicationEvent(disableEvent)
      val result = remotePluginsCache.get(enableEvent.pluginId)
      expectThat(result).isNull()
    }
  }

  private class Fixture {
    val objectMapper: ObjectMapper = mockk(relaxed = true)
    val okHttpClientProvider: OkHttpClientProvider = mockk(relaxed = true)
    val objectMapperProvider: ObjectProvider<ObjectMapper> = mockk(relaxed = true)
    val okHttpClientProviderProvider: ObjectProvider<OkHttpClientProvider> = mockk(relaxed = true)
    val remotePluginsCache: RemotePluginsCache = RemotePluginsCache(mockk(relaxed = true))

    val enableEvent = RemotePluginConfigChanged(
      source = mockk(relaxed = true),
      status = RemotePluginConfigChanged.Status.ENABLED,
      pluginId = "netflix.remote1",
      version = "0.0.1",
      remoteExtensionConfigs = listOf(
        RemoteExtensionConfig(
          "type",
          "netflix.remote.extension",
          RemoteExtensionConfig.RemoteExtensionTransportConfig(
            RemoteExtensionConfig.RemoteExtensionTransportConfig.Http(
              "https://example.com",
              mutableMapOf(),
              mutableMapOf(),
              mockk(relaxed = true)
            )
          ),
          mutableMapOf()
        )
      )
    )

    val updateEvent = RemotePluginConfigChanged(
      source = mockk(relaxed = true),
      status = RemotePluginConfigChanged.Status.UPDATED,
      pluginId = "netflix.remote1",
      version = "0.0.2",
      remoteExtensionConfigs = listOf(
        RemoteExtensionConfig(
          "type",
          "netflix.remote.extension",
          RemoteExtensionConfig.RemoteExtensionTransportConfig(
            RemoteExtensionConfig.RemoteExtensionTransportConfig.Http(
              "https://example.com",
              mutableMapOf(),
              mutableMapOf(),
              mockk(relaxed = true)
            )
          ),
          mutableMapOf()
        )
      )
    )

    val disableEvent = RemotePluginConfigChanged(
      source = mockk(relaxed = true),
      status = RemotePluginConfigChanged.Status.DISABLED,
      pluginId = "netflix.remote1",
      version = "0.0.1",
      remoteExtensionConfigs = listOf(
        RemoteExtensionConfig(
          "type",
          "netflix.remote.extension",
          RemoteExtensionConfig.RemoteExtensionTransportConfig(
            RemoteExtensionConfig.RemoteExtensionTransportConfig.Http(
              "https://example.com",
              mutableMapOf(),
              mutableMapOf(),
              mockk(relaxed = true)
            )
          ),
          mutableMapOf()
        )
      )
    )

    val remoteExtensionPointDefinition: RemoteExtensionPointDefinition = object : RemoteExtensionPointDefinition {
      override fun type() = "type"
      override fun configType(): Class<out ConfigType> = ConfigType::class.java
    }

    val subject: RemotePluginConfigChangedListener = RemotePluginConfigChangedListener(
      objectMapperProvider,
      SubtypeProvider(),
      okHttpClientProviderProvider,
      remotePluginsCache,
      listOf(remoteExtensionPointDefinition)
    )

    init {
      every { objectMapperProvider.getObject() } returns objectMapper
      every { okHttpClientProviderProvider.getObject() } returns okHttpClientProvider
      every { objectMapper.convertValue(any(), ConfigType::class.java) } returns ConfigType()
    }
  }

  private class ConfigType: RemoteExtensionPointConfig

  private class SubtypeProvider : ObjectProvider<List<ObjectMapperSubtypeConfigurer.SubtypeLocator>> {
    override fun getObject(vararg args: Any?): List<ObjectMapperSubtypeConfigurer.SubtypeLocator> {
      return emptyList()
    }

    override fun getObject(): List<ObjectMapperSubtypeConfigurer.SubtypeLocator> {
      return emptyList()
    }

    override fun getIfAvailable(): List<ObjectMapperSubtypeConfigurer.SubtypeLocator>? {
      return emptyList()
    }

    override fun getIfUnique(): List<ObjectMapperSubtypeConfigurer.SubtypeLocator>? {
      return emptyList()
    }

  }
}
