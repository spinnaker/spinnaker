package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import java.time.Clock
import org.springframework.stereotype.Component

@Component
class ManualJudgementConstraintEvaluator(
  override val deliveryConfigRepository: DeliveryConfigRepository,
  private val clock: Clock
) : StatefulConstraintEvaluator<ManualJudgementConstraint>() {

  override val supportedType = SupportedConstraintType<ManualJudgementConstraint>("manual-judgement")

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: ManualJudgementConstraint,
    state: ConstraintState
  ): Boolean {
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

    return state.passed()
  }
}
