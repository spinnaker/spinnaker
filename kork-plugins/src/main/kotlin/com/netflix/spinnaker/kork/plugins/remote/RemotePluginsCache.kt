/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.kork.plugins.remote

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.netflix.spinnaker.kork.plugins.events.RemotePluginCacheRefresh
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

/**
 * Provides a read/write remote plugin cache used by either the [RemotePluginConfigChangedListener]
 * to add plugins based on configuration changes or the [RemotePluginsProvider] to retrieve plugins
 * for use in services.
 */
class RemotePluginsCache(
  val applicationEventPublisher: ApplicationEventPublisher
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val remotePluginsCache: Cache<String, RemotePlugin> = Caffeine.newBuilder()
    .build<String, RemotePlugin>()

  /** Get the remote plugin by plugin ID, returns null if the plugin is not found. */
  internal fun get(pluginId: String): RemotePlugin? {
    return remotePluginsCache.getIfPresent(pluginId)
  }

  /** Return all plugins in the remote plugin cache. */
  internal fun getAll(): Map<String, RemotePlugin> {
    return remotePluginsCache.asMap()
  }

  /** Put a plugin in the cache, emits a [RemotePluginCacheRefresh] event. */
  internal fun put(remotePlugin: RemotePlugin) {
    remotePluginsCache.put(remotePlugin.id, remotePlugin)
    applicationEventPublisher.publishEvent(RemotePluginCacheRefresh(this, remotePlugin.id))
    log.debug("Put remote plugin '{}'.", remotePlugin.id)
  }

  /** Remove the specified plugin from the cache, emits a [RemotePluginCacheRefresh] event. */
  internal fun remove(pluginId: String) {
    remotePluginsCache.invalidate(pluginId)
    applicationEventPublisher.publishEvent(RemotePluginCacheRefresh(this, pluginId))
    log.debug("Removed remote plugin '{}'.", pluginId)
  }
}
