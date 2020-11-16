package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.Verification

data class TestContainerVerification(
  val container: String
) : Verification {
  override val type = "test-container"

  override val id = "$type:$container"
}
