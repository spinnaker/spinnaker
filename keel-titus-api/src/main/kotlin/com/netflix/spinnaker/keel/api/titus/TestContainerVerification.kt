package com.netflix.spinnaker.keel.api.titus

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location

data class TestContainerVerification(
  val repository: String,
  val tag: String = "latest",
  val location: Location,
  val application: String? = null
) : Verification {
  override val type = TYPE
  override val id = "$repository:$tag"

  companion object {
    const val TYPE = "test-container"
  }
}
