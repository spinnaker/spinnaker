package com.netflix.spinnaker.orca.remote.service

import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsProvider
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension
import com.netflix.spinnaker.orca.remote.RemoteStageExtensionPointDefinition
import com.netflix.spinnaker.orca.remote.model.RemoteStageExtensionPointConfig

/**
 * Service to provide remote stages.  Interacts with the [RemotePluginsProvider] to
 * find remote plugins and extensions from the remote plugins cache.
 */
class RemoteStageExtensionService(
  private val remotePluginsProvider: RemotePluginsProvider,
  private val remoteStageExtensionDefinition: RemoteStageExtensionPointDefinition
) {

  /**
   * Get remote stage by type.  To ensure you are getting the latest remote stage extension
   * configuration, call this prior to initializing the remote stage tasks.
   */
  fun getByStageType(stageType: String): RemoteExtension {
    val remoteExtensions = remotePluginsProvider
      .getExtensionsByType(remoteStageExtensionDefinition.type())
    val remoteStageExtensions: MutableList<RemoteExtension> = mutableListOf()

    remoteExtensions.forEach { extension ->
      val config = extension.getTypedConfig<RemoteStageExtensionPointConfig>()

      if (config.type == stageType) {
        remoteStageExtensions.add(extension)
      }
    }

    if (remoteStageExtensions.size > 1) throw DuplicateRemoteStageTypeException(stageType, remoteStageExtensions)
    return if (remoteStageExtensions.isNotEmpty()) remoteStageExtensions.first() else throw RemoteStageTypeNotFoundException(stageType)
  }
}
