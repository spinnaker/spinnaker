package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize(`as` = DependsOnConstraint::class)
sealed class Constraint

/**
 * A constraint that requires that an artifact has been successfully promoted to a previous
 * environment first.
 */
data class DependsOnConstraint(
  val environment: String
) : Constraint()
