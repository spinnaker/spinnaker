package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.StatelessConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintAttributesType
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator.Companion.getConstraintForEnvironment
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType.POST_DEPLOY
import com.netflix.spinnaker.keel.api.action.ActionType.VERIFICATION
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class DependsOnConstraintEvaluator(
  private val artifactRepository: ArtifactRepository,
  private val actionRepository: ActionRepository,
  override val eventPublisher: EventPublisher,
  private val clock: Clock
) : StatelessConstraintEvaluator<DependsOnConstraint, DependsOnConstraintAttributes> {
  companion object {
    const val CONSTRAINT_NAME = "depends-on"
  }
  
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val supportedType = SupportedConstraintType<DependsOnConstraint>("depends-on")
  override val attributeType = SupportedConstraintAttributesType<DependsOnConstraintAttributes>("depends-on")

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean {
    val constraint = getConstraintForEnvironment(deliveryConfig, targetEnvironment.name, supportedType.type)

    val requiredEnvironment = deliveryConfig
      .environments
      .firstOrNull { it.name == constraint.environment }
    requireNotNull(requiredEnvironment) {
      "No environment named ${constraint.environment} exists in the configuration ${deliveryConfig.name}"
    }

    val context = ArtifactInEnvironmentContext(deliveryConfig, requiredEnvironment.name, artifact.reference, version)

    return artifactRepository.wasSuccessfullyDeployedTo(
      deliveryConfig,
      artifact,
      version,
      requiredEnvironment.name
    ) && actionRepository.allPassed(context, VERIFICATION)
     && actionRepository.allStarted(context, POST_DEPLOY)
  }

  override fun generateConstraintStateSnapshot(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    currentStatus: ConstraintStatus?
  ): ConstraintState {
    val constraint = getConstraintForEnvironment(deliveryConfig, targetEnvironment.name, supportedType.type)
    val status = currentStatus
      ?: if (canPromote(artifact, version, deliveryConfig, targetEnvironment)) {
        PASS
      } else {
        FAIL
      }

    return ConstraintState(
      deliveryConfigName = deliveryConfig.name,
      environmentName = targetEnvironment.name,
      artifactVersion = version,
      artifactReference = artifact.reference,
      type = CONSTRAINT_NAME,
      status = status,
      attributes = DependsOnConstraintAttributes(
        dependsOnEnvironment = constraint.environment,
      ),
      judgedAt = clock.instant(),
      judgedBy = "Spinnaker"
    )
  }
}
