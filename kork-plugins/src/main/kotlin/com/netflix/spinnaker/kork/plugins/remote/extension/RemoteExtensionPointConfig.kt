package com.netflix.spinnaker.kork.plugins.remote.extension

/**
 * Root type of remote extension point configurations, implemented in Spinnaker services.
 */
interface RemoteExtensionPointConfig

/**
 * No-op in the case wherein a remote extension does not have any necessary configuration.
 *
 * @param type The type of remote extension, defaults to "noop"
 */
data class NoOpRemoteExtensionPointConfig(val type: String = "noop"): RemoteExtensionPointConfig
