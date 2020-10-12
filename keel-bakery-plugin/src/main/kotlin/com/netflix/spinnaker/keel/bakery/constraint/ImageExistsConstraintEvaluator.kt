package com.netflix.spinnaker.keel.bakery.constraint

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.plugins.ConstraintEvaluator
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.bakery.api.ImageExistsConstraint
import com.netflix.spinnaker.keel.caffeine.CacheFactory
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.getConfig
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.exceptions.SystemException
import kotlinx.coroutines.future.await
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
  override val eventPublisher: EventPublisher,
  cacheFactory: CacheFactory
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

  private data class Key(
    val account: String,
    val version: String,
    val regions: Set<String>
  )

  private val cache = cacheFactory
    .asyncLoadingCache<Key, NamedImage>(cacheName = "namedImages") { key ->
      log.debug("Searching for baked image for {} in {}", key.version, key.regions.joinToString())
      imageService.getLatestNamedImageWithAllRegionsForAppVersion(
        // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
        appVersion = AppVersion.parseName(key.version)
          ?: throw SystemException("Invalid AMI app version: ${key.version}"),
        account = key.account,
        regions = key.regions
      )
    }

  private fun findMatchingImage(version: String, vmOptions: VirtualMachineOptions): NamedImage? =
    runBlocking {
      cache.get(Key(defaultImageAccount, version, vmOptions.regions)).await()
    }

  private val defaultImageAccount: String
    get() = dynamicConfigService.getConfig("images.default-account", "test")

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
