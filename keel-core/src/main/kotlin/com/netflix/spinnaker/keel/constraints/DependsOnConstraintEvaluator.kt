package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator.Companion.getConstraintForEnvironment
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationStatus
import com.netflix.spinnaker.keel.api.verification.VerificationStatus.*
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DependsOnConstraintEvaluator(
  private val artifactRepository: ArtifactRepository,
  private val verificationRepository: VerificationRepository,
  override val eventPublisher: EventPublisher
) : ConstraintEvaluator<DependsOnConstraint> {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  override val supportedType = SupportedConstraintType<DependsOnConstraint>("depends-on")


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
    return artifactRepository.wasSuccessfullyDeployedTo(
      deliveryConfig,
      artifact,
      version,
      requiredEnvironment.name
    ) && allVerificationsSucceededIn(
      deliveryConfig,
      artifact,
      version,
      requiredEnvironment
    )
  }

  private fun allVerificationsSucceededIn(
    deliveryConfig: DeliveryConfig,
    artifact: DeliveryArtifact,
    version: String,
    environment: Environment
  ) : Boolean {

    val context = VerificationContext(deliveryConfig, environment.name, artifact.reference, version)
    val states = verificationRepository.getStates(context)

    return environment.verifyWith
      .map { it.id }
      .all { id ->
        when (states[id]?.status) {
          PASSED -> true.also {
            log.info("verification ($id) passed against version $version for app ${deliveryConfig.application}")
          }
          FAILED -> false.also {
            log.info("verification ($id) failed against version $version for app ${deliveryConfig.application}")
          }
          RUNNING -> false.also {
            log.info("verification ($id) still running against version $version for app ${deliveryConfig.application}")
          }
          null -> false.also {
            log.info("no database entry for verification ($id) against version $version for app ${deliveryConfig.application}")
          }
        }
      }
  }
}
