package com.netflix.spinnaker.keel.preview

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * This is an implicit constraint that is used to prevent promotion of artifact versions to
 * a preview environment if the source branch of the artifact does not match the branch associated
 * with the preview environment.
 */
@Component
class PreviewEnvironmentBranchConstraintEvaluator(
  private val artifactRepository: ArtifactRepository,
  override val eventPublisher: EventPublisher
) : ConstraintEvaluator<PreviewEnvironmentBranchConstraint> {

  override fun isImplicit(): Boolean = true

  override val supportedType = SupportedConstraintType<PreviewEnvironmentBranchConstraint>("preview-environment")

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean {
    if (targetEnvironment.isPreview) {
      val versionDetails = artifactRepository.getArtifactVersion(artifact, version)
      if (versionDetails?.branch == null) {
        log.debug("Rejecting version $version of $artifact in preview $targetEnvironment due to missing git metadata")
        return false
      } else if (versionDetails.branch != targetEnvironment.branch) {
        log.debug("Rejecting version $version of $artifact in preview $targetEnvironment as branches don't match" +
          " (${versionDetails.branch} != ${targetEnvironment.branch}")
        return false
      }
    }
    return true
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
