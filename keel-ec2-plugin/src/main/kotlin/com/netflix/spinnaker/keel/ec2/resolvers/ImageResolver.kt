package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.VirtualMachineImage
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.getLatestNamedImages
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.clouddriver.model.baseImageName
import com.netflix.spinnaker.keel.ec2.NoArtifactVersionHasBeenApproved
import com.netflix.spinnaker.keel.ec2.NoImageFoundForRegions
import com.netflix.spinnaker.keel.filterNotNullValues
import com.netflix.spinnaker.keel.getConfig
import com.netflix.spinnaker.keel.parseAppVersion
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoMatchingArtifactException
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ImageResolver(
  private val dynamicConfigService: DynamicConfigService,
  private val repository: KeelRepository,
  private val imageService: ImageService,
  private val bakedImageRepository: BakedImageRepository
) : Resolver<ClusterSpec> {

  override val supportedKind = EC2_CLUSTER_V1_1

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  data class VersionedNamedImage(
    val bakedVmImages: Map<String, VirtualMachineImage>, // from our store of what we baked
    val vmImages: Map<String, VirtualMachineImage>, // from clouddriver
    val artifact: DeliveryArtifact,
    val version: String
  )

  override fun invoke(resource: Resource<ClusterSpec>): Resource<ClusterSpec> {
    val ref = resource.spec.artifactReference ?: return resource

    val image = runBlocking {
      resource.resolveFromReference(ref)
    }
    return resource.withVirtualMachineImages(image)
  }

  val defaultImageAccount: String
    get() = dynamicConfigService.getConfig("images.default-account", "test")

  private suspend fun Resource<ClusterSpec>.resolveFromReference(
    ref: String
  ): VersionedNamedImage {
    val deliveryConfig = repository.deliveryConfigFor(id)
    val artifact = deliveryConfig.artifacts.find { it.reference == ref && it.type == DEBIAN }
      ?: throw NoMatchingArtifactException(deliveryConfig.name, DEBIAN, ref)

    return this.resolveFromArtifact(artifact as DebianArtifact)
  }

  private suspend fun Resource<ClusterSpec>.resolveFromArtifact(
    artifact: DebianArtifact
  ): VersionedNamedImage {
    val deliveryConfig = repository.deliveryConfigFor(id)
    val environment = repository.environmentFor(id)
    val account = defaultImageAccount
    val regions = spec.locations.regions.map { it.name }

    val artifactVersion = repository.latestVersionApprovedIn(
      deliveryConfig,
      artifact,
      environment.name
    ) ?: throw NoArtifactVersionHasBeenApproved(artifact.name, environment.name)

    val images = imageService.getLatestNamedImages(
      appVersion = artifactVersion.parseAppVersion(),
      account = account,
      regions = regions,
      baseOs = artifact.vmOptions.baseOs
    )

    val imageIdByRegion: Map<String, String> = images
      .mapValues { (region, image) -> image.amis[region]?.first() }
      .filterNotNullValues()

     val vmImages = images.mapValues { (region, namedImage) ->
      VirtualMachineImage(
        id = imageIdByRegion.getValue(region),
        appVersion = namedImage.appVersion,
        baseImageName = namedImage.baseImageName
      )
    }

    // we might have pre-knowledge of baked images here, so let's load that.
    val bakedVmImages: Map<String, VirtualMachineImage> = bakedImageRepository
      .getByArtifactVersion(artifactVersion, artifact)
      ?.let { bakedImage ->
        bakedImage.amiIdsByRegion.mapValues { (region, amiId) ->
          VirtualMachineImage(
            id = amiId,
            appVersion = bakedImage.appVersion,
            baseImageName = bakedImage.baseAmiName
          )
        }.toMap()
      } ?: emptyMap()

    return VersionedNamedImage(
      bakedVmImages = bakedVmImages,
      vmImages = vmImages,
      artifact = artifact,
      version = artifactVersion
    )
  }

  private fun Resource<ClusterSpec>.withVirtualMachineImages(image: VersionedNamedImage): Resource<ClusterSpec> {
    val requiredRegions = spec.locations.regions.map { it.name }

    val missingRegions = requiredRegions - image.vmImages.keys
    val missingBakedRegions = requiredRegions - image.bakedVmImages.keys
    if (missingRegions.isNotEmpty() && missingBakedRegions.isNotEmpty()) {
      log.warn("Missing regions for ${image.version}. Clouddriver has {}, we have {}", missingRegions, missingBakedRegions)
      throw NoImageFoundForRegions(image.version, missingRegions.intersect(missingBakedRegions))
    }

    val overrides = mutableMapOf<String, ServerGroupSpec>()
    overrides.putAll(spec.overrides)

    requiredRegions.forEach { region ->
      // fall back to looking at our data about baked images
      val vmImage = image.vmImages[region] ?: image.bakedVmImages.getValue(region)
      overrides[region] = overrides[region]
        .withVirtualMachineImage(vmImage)
    }

    return copy(
      spec = spec.copy(
        overrides = overrides,
        artifactName = image.artifact.name,
        artifactVersion = image.version
      )
    )
  }

  private fun ServerGroupSpec?.withVirtualMachineImage(image: VirtualMachineImage) =
    (this ?: ServerGroupSpec()).run {
      copy(
        launchConfiguration = launchConfiguration.run {
          (this ?: LaunchConfigurationSpec()).copy(image = image)
        }
      )
    }
}
