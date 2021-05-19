package com.netflix.spinnaker.keel.api.titus

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location

data class TestContainerVerification(
  /**
   * Image name with optional tag, e.g.
   *
   *   "acme/widget"
   *   "acme/widget:stable"
   *
   * This will become non-nullable once repository/tag are removed
   */
  val image: String? = null,

  @Deprecated("replaced by image field")
  val repository: String? = null,

  @Deprecated("replaced by image field")
  val tag: String? = "latest",

  val location: Location,
  val application: String? = null,

  val entrypoint: String? = null
) : Verification {
  override val type = TYPE
  override val id = imageId

  /**
   * Determine imageId depending on the newer field (image) or the deprecated fields (repository, tag)
   */
  val imageId : String
    get() =
      when {
        image != null &&  image.contains(":") -> image
        image != null && !image.contains(":") -> "${image}:latest"

        repository != null && tag != null -> "${repository}:${tag}"
        repository != null && tag == null -> "${repository}:latest"
        else -> error("no container image specified")
      }

  companion object {
    const val TYPE = "test-container"
  }
}
