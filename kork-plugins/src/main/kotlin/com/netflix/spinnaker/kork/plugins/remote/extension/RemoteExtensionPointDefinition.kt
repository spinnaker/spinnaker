package com.netflix.spinnaker.kork.plugins.remote.extension

/**
 * Define properties of the remote extension. Spinnaker services must implement this and provide it
 * as a Spring bean in order for remote plugin extensions to be resolved from the configuration.
 */
interface RemoteExtensionPointDefinition {

  /**
   * The remote extension type.
   */
  fun type(): String

  /**
   * The remote extension configuration type.
   */
  fun configType(): Class<out RemoteExtensionPointConfig> = NoOpRemoteExtensionPointConfig::class.java
}
