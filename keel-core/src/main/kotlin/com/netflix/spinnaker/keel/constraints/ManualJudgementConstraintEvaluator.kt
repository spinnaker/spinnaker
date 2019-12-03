package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator.Companion.getConstraintForEnvironment
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ManualJudgementConstraintEvaluator(
  private val deliveryConfigRepository: DeliveryConfigRepository,
  private val clock: Clock
) : ConstraintEvaluator<ManualJudgementConstraint> {

  override val constraintType = ManualJudgementConstraint::class.java

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean {
    val constraint = getConstraintForEnvironment(deliveryConfig, targetEnvironment.name, constraintType)

    val state = deliveryConfigRepository
      .getConstraintState(
        deliveryConfig.name,
        targetEnvironment.name,
        version,
        constraint.type)
      ?: ConstraintState(
        deliveryConfigName = deliveryConfig.name,
        environmentName = targetEnvironment.name,
        artifactVersion = version,
        type = constraint.type,
        status = ConstraintStatus.PENDING
      )
        .also {
          deliveryConfigRepository.storeConstraintState(it)
          // TODO: Emit an event here, requesting a manual judgement
        }

    if (state.failed()) {
      return false
    }

    if (state.timedOut(constraint.timeout, clock.instant())) {
      deliveryConfigRepository
        .storeConstraintState(
          state.copy(
            status = ConstraintStatus.FAIL,
            comment = "Timed out after ${constraint.timeout}"
          )
        )

      // TODO: Emit event
      return false
    }

    return state.canPromote()
  }
}
