package com.netflix.spinnaker.keel.bakery.constraint

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.bakery.api.ImageExistsConstraint
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.getConfig
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher

/**
 * This is an implicit constraint that is used to prevent promotion of a Debian artifact version to
 * an environment before an AMI has been baked. If we allow the version to promote before that, any
 * clusters in the environment will error on their resource checks because they will be unable to
 * find the AMI.
 */
class ImageExistsConstraintEvaluator(
  private val imageService: ImageService,
  private val dynamicConfigService: DynamicConfigService,
  override val eventPublisher: ApplicationEventPublisher
) : ConstraintEvaluator<ImageExistsConstraint> {

  override fun isImplicit(): Boolean = true

  override val supportedType = SupportedConstraintType<ImageExistsConstraint>("bake")

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean {
    if (artifact !is DebianArtifact) {
      return true
    }

    val image = findMatchingImage(version, artifact.vmOptions)
    return image != null
  }

  private fun findMatchingImage(version: String, vmOptions: VirtualMachineOptions): NamedImage? {
    log.debug("Searching for baked image for %s in %s", version, vmOptions.regions.joinToString())
    return runBlocking {
      imageService.getLatestNamedImageWithAllRegionsForAppVersion(
        AppVersion.parseName(version),
        defaultImageAccount,
        vmOptions.regions
      )
    }
  }

  private val defaultImageAccount: String
    get() = dynamicConfigService.getConfig("images.default-account", "test")

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
