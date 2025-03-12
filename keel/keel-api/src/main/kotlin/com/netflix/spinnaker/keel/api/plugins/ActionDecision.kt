package com.netflix.spinnaker.keel.api.plugins

data class ActionDecision(
  val willAct: Boolean = true,
  val message: String? = null
)
