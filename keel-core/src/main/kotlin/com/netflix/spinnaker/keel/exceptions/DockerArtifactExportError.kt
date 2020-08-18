package com.netflix.spinnaker.keel.exceptions

import com.netflix.spinnaker.kork.exceptions.UserException

class DockerArtifactExportError(tags: List<String>, container: String) :
  UserException(
    "Unable to determine tag strategy for docker images with tags ${tags.joinToString()} from container ($container), please supply a custom regex " +
      "(see https://www.spinnaker.io/guides/user/managed-delivery/artifacts/#advanced-configuration for more information)"
  )
