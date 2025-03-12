package com.netflix.spinnaker.keel.api.titus

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location

data class TestContainerVerification(
  val image: String,
  val location: Location,
  val application: String? = null,
  val entrypoint: String? = null,
  val env: Map<String, String> = emptyMap()
) : Verification {
  override val type = TYPE
  override val id by lazy {
    "$image@${location.account}/${location.region}${entrypoint?.let { "#${it.hash}" } ?: "" }"
  }

  private val String.hash get() = Integer.toHexString(hashCode())

  val imageId: String
    get() =
      if (image.contains(":")) image
      else "${image}:latest"

  companion object {
    const val TYPE = "test-container"
  }
}
