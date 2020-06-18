package com.netflix.spinnaker.keel.api.artifacts

/**
 * todo eb: other information should go here, like a link to the jenkins build. But that needs to be done
 * in a scalable way. For now, this is just a minimal container for information we can parse from the version.
 */
data class BuildMetadata(
  val id: Int
)
