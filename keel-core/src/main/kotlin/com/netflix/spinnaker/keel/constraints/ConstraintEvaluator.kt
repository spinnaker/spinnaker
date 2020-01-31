package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.Constraint
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator.Companion.getConstraintForEnvironment
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository

interface ConstraintEvaluator<T : Constraint> {

  companion object {
    fun <T> getConstraintForEnvironment(
      deliveryConfig: DeliveryConfig,
      targetEnvironment: String,
      klass: Class<T>
    ): T {
      val target = deliveryConfig.environments.firstOrNull { it.name == targetEnvironment }
      requireNotNull(target) {
        "No environment named $targetEnvironment exists in the configuration ${deliveryConfig.name}"
      }

      return target
        .constraints
        .filterIsInstance(klass)
        .first()
    }
  }

  val supportedType: SupportedConstraintType<T>

  fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean
}

data class SupportedConstraintType<T : Constraint>(
  val name: String,
  val type: Class<T>
)

inline fun <reified T : Constraint> SupportedConstraintType(name: String): SupportedConstraintType<T> =
  SupportedConstraintType(name, T::class.java)

abstract class StatefulConstraintEvaluator<T : Constraint> : ConstraintEvaluator<T> {
  abstract val deliveryConfigRepository: DeliveryConfigRepository

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean {
    val constraint = getConstraintForEnvironment(deliveryConfig, targetEnvironment.name, supportedType.type)
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
          // TODO: Emit an event here?
        }

    return when {
      state.failed() -> false
      state.passed() -> true
      else -> canPromote(artifact, version, deliveryConfig, targetEnvironment, constraint, state)
    }
  }

  abstract fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: T,
    state: ConstraintState
  ): Boolean
}
