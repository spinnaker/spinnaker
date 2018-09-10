package com.netflix.spinnaker.keel.registry

import com.netflix.appinfo.InstanceInfo
import com.netflix.appinfo.InstanceInfo.Builder.newBuilder
import com.netflix.discovery.EurekaClient
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyVararg
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

internal object PluginMonitorSpec : Spek({

  val pluginRepository: PluginRepository = mock()
  val eureka: EurekaClient = mock()
  val counter: Counter = mock()
  val registry: Registry = mock() {
    on(it.counter(PluginMonitor.MISSING_PLUGIN_COUNTER)) doReturn counter
    on(it.counter(eq(PluginMonitor.MISSING_PLUGIN_COUNTER), anyVararg<String>())) doReturn counter
  }
  val subject = PluginMonitor(pluginRepository, eureka, registry)

  val addresses = listOf(
    PluginAddress("Plugin 1", "keel-plugin1.test.netflix.net", 6565),
    PluginAddress("Plugin 2", "keel-plugin2.test.netflix.net", 6565)
  )

  beforeGroup {
    whenever(pluginRepository.allPlugins()) doReturn addresses
  }

  afterGroup {
    reset(pluginRepository)
  }

  given("all plugin have available instances in Eureka") {
    beforeGroup {
      whenever(eureka.getInstancesByVipAddress(any(), any())) doAnswer { instanceForVip(it.arguments.first() as String) }
    }

    afterGroup { reset(eureka, counter) }

    on("running the monitor") {
      subject.checkPluginAvailability()
    }

    it("does nothing") {
      verifyZeroInteractions(counter)
    }
  }

  given("a plugin has no available instances in Eureka") {
    beforeGroup {
      whenever(eureka.getInstancesByVipAddress(eq(addresses.first().vip), any())) doReturn emptyList<InstanceInfo>()
      whenever(eureka.getInstancesByVipAddress(eq(addresses.last().vip), any())) doAnswer { instanceForVip(it.arguments.first() as String) }
    }

    afterGroup { reset(eureka, counter) }

    on("running the monitor") {
      subject.checkPluginAvailability()
    }

    it("increments a counter") {
      verify(counter).increment()
    }

    it("tags the counter with the plugin name") {
      verify(registry).counter(PluginMonitor.MISSING_PLUGIN_COUNTER, addresses.first().name)
    }
  }
})

fun instanceForVip(vip: String): List<InstanceInfo> =
  vip.substringBefore(".").let { app ->
    listOf(
      newBuilder()
        .apply {
          setAppName(app)
          setASGName("$app-v001")
        }.build()
    )
  }
