package com.netflix.spinnaker.keel.registry

import com.netflix.discovery.EurekaClient
import com.netflix.spectator.api.Registry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PluginMonitor(
  private val pluginRepository: PluginRepository,
  private val eureka: EurekaClient,
  private val registry: Registry
) {

  @Scheduled(fixedDelayString = "\${keel.health.plugin.availability.frequency.ms:300000}")
  fun checkPluginAvailability() {
    pluginRepository.allPlugins().forEach { address ->
      val instances = eureka.getInstancesByVipAddress(address.vip, false)
      if (instances.isEmpty()) {
        log.warn("Found no instances of \"${address.name}\" in Eureka")
        registry.counter(MISSING_PLUGIN_COUNTER, address.name).increment()
      } else {
        log.debug("Found ${instances.size} instance(s) of \"${address.name}\" in Eureka")
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    const val MISSING_PLUGIN_COUNTER: String = "keel.plugin.unavailable"
  }
}
