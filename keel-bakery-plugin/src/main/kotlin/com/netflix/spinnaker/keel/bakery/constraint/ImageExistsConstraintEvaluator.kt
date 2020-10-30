package com.netflix.spinnaker.keel.bakery.constraint

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.bakery.api.ImageExistsConstraint
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.getLatestNamedImages
import com.netflix.spinnaker.keel.getConfig
import com.netflix.spinnaker.keel.parseAppVersion
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * This is an implicit constraint that is used to prevent promotion of a Debian artifact version to
 * an environment before an AMI has been baked. If we allow the version to promote before that, any
 * clusters in the environment will error on their resource checks because they will be unable to
 * find the AMI.
 */
@Component
class ImageExistsConstraintEvaluator(
  private val imageService: ImageService,
  private val dynamicConfigService: DynamicConfigService,
  override val eventPublisher: EventPublisher
) : ConstraintEvaluator<ImageExistsConstraint> {

  override fun isImplicit(): Boolean = true

  override val supportedType = SupportedConstraintType<ImageExistsConstraint>("bake")

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment
  ): Boolean =
    artifact !is DebianArtifact || imagesExistInAllRegions(version, artifact.vmOptions)

  private fun imagesExistInAllRegions(version: String, vmOptions: VirtualMachineOptions): Boolean =
    runBlocking {
      imageService.getLatestNamedImages(
        appVersion = version.parseAppVersion(),
        account = defaultImageAccount,
        regions = vmOptions.regions
      )
    }
      .also {
        if (it.keys.containsAll(vmOptions.regions)) {
          log.info("Found AMIs for all desired regions for {}", version)
        } else {
          log.warn("Missing regions {} for {}", (vmOptions.regions - it.keys).sorted().joinToString(), version)
        }
      }
      .keys.containsAll(vmOptions.regions)

  private val defaultImageAccount: String
    get() = dynamicConfigService.getConfig("images.default-account", "test")

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
