package com.netflix.spinnaker.orca.remote.model

import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPointConfig

/**
 * Configuration for a remote stage extension point.
 */
data class RemoteStageExtensionPointConfig(

  /** The stage type. */
  val type: String,

  /** Stage description. */
  val description: String,

  /** The stage label, typically a shorter description displayed when searching for stage types. */
  val label: String,

  /** The map of stage input parameters. */
  val parameters: MutableMap<String, Any?> = mutableMapOf()
) : RemoteExtensionPointConfig
