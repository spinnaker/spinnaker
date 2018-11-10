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
package com.netflix.spinnaker.keel.plugin

import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener

@Deprecated("not needed if we use k8s")
class PluginRegistrar(
  private val plugins: List<KeelPlugin>,
  private val pluginRegistry: PluginRegistry
) : ApplicationListener<RemoteStatusChangedEvent> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override fun onApplicationEvent(event: RemoteStatusChangedEvent) {
    if (event.source.status == UP) {
      onDiscoveryUp()
    }
    // TODO: should deregister as well
  }

  fun onDiscoveryUp() {
    plugins.forEach { plugin ->
      log.info("Registering {} with Keel", plugin.name)
      when (plugin) {
        is AssetPlugin -> pluginRegistry.register(plugin)
        is VetoPlugin -> pluginRegistry.register(plugin)
      }
    }
  }
}
