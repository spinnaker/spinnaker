package com.netflix.spinnaker.keel.front50.model

/**
 * A trigger in a Spinnaker [Pipeline].
 */
data class Trigger(
  val type: String,
  val enabled: Boolean,
  val application: String? = null,
  val pipeline: String? = null
)
