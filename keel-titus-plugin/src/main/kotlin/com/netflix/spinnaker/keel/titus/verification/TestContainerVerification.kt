package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location

data class TestContainerVerification(
  val repository: String,
  val tag: String = "latest",
  val location: Location
) : Verification {
  override val type: String = TYPE
  override val id: String = "$repository/$tag"

  companion object {
    const val TYPE = "test-container"
  }
}
