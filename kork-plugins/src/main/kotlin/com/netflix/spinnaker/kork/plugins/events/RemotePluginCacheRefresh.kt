package com.netflix.spinnaker.kork.plugins.events

import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsCache
import org.springframework.context.ApplicationEvent

/**
 * A Spring [ApplicationEvent] that is emitted when the remote plugins cache is changed.
 *
 * The remote plugins cache is the cache of resolved remote plugins with remote transport
 * clients.  This can optionally be used in Spinnaker services to ensure a remote
 * extension point implementation has the latest cached version of the remote plugin.
 *
 * @param source The source of the event
 * @param pluginId The plugin ID that triggered this cache refresh event
 */
data class RemotePluginCacheRefresh(
  private val source: RemotePluginsCache,
  val pluginId: String
) : ApplicationEvent(source)
