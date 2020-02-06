package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.Environment

sealed class ConstraintEvent(
  open val environment: Environment,
  open val constraint: Constraint
)

data class ConstraintStateChanged(
  override val environment: Environment,
  override val constraint: Constraint,
  val previousState: ConstraintState?,
  val currentState: ConstraintState
) : ConstraintEvent(environment, constraint)
