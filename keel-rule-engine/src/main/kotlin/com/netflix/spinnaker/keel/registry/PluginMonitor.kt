/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        registry.counter(MISSING_PLUGIN_COUNTER, "name" to address.name).increment()
      } else {
        log.info("Found ${instances.size} instance(s) of \"${address.name}\" in Eureka")
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  companion object {
    const val MISSING_PLUGIN_COUNTER: String = "keel.plugin.unavailable"
  }
}

fun Registry.counter(name: String, vararg tags: Pair<String, String>) =
  counter(name, *tags.flatMap { listOf(it.first, it.second) }.toTypedArray())
