package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes

data class DependsOnConstraintAttributes(
  val dependsOnEnvironment: String,
  val currentlyPassing: Boolean = true
) : ConstraintStateAttributes("depends-on")
