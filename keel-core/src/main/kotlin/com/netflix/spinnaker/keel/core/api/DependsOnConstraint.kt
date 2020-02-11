package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.Constraint

/**
 * A constraint that requires that an artifact has been successfully promoted to a previous
 * environment first.
 */
data class DependsOnConstraint(
  val environment: String
) : Constraint("depends-on")
