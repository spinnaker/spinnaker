package com.netflix.spinnaker.orca.remote.service

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension

/** Thrown when the requested remote stage type is not found. */
class RemoteStageTypeNotFoundException(stageType: String) : IntegrationException(
  "Remote stage type $stageType not found.  Check if stage type exists in plugin info configuration."
)

/** Thrown if there are multiple remote stage configurations of the same stage type. */
class DuplicateRemoteStageTypeException(stageType: String, remoteExtensions: List<RemoteExtension>) : SystemException(
  "Duplicate stage type $stageType found.  Multiple plugins define the same stage type: ${remoteExtensions.map { it.pluginId }}"
)
