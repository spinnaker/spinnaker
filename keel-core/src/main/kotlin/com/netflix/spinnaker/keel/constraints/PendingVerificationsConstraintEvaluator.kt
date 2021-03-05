package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.core.api.PendingVerificationsConstraint
import org.slf4j.LoggerFactory

class PendingVerificationsConstraintEvaluator(
  private val verificationRepository: VerificationRepository,
  override val eventPublisher: EventPublisher
) : ConstraintEvaluator<PendingVerificationsConstraint> {

  override fun isImplicit() = true

  override val supportedType =
    SupportedConstraintType<PendingVerificationsConstraint>("pending-verifications")

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean =
    if (targetEnvironment.verifyWith.isEmpty()) {
      log.debug("{} / {} has no verifications", deliveryConfig.name, targetEnvironment.name)
      true
    } else {
      val pendingVerifications =
        verificationRepository.pendingInEnvironment(deliveryConfig, targetEnvironment.name)
      if (pendingVerifications.isNotEmpty()) {
        log.info(
          "{} / {} is awaiting results from {} before another deployment can proceed",
          deliveryConfig.name,
          targetEnvironment.name,
          pendingVerifications.map { it.verification.id }.joinToString()
        )
        false
      } else {
        log.debug(
          "{} / {} has no pending verifications",
          deliveryConfig.name,
          targetEnvironment.name
        )
        true
      }
    }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
