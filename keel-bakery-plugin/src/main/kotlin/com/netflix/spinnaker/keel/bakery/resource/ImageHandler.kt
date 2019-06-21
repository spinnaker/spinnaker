package com.netflix.spinnaker.keel.bakery.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.NoKnownArtifactVersions
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.Image
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ImageHandler(
  override val objectMapper: ObjectMapper,
  private val artifactRepository: ArtifactRepository,
  private val baseImageCache: BaseImageCache,
  private val cloudDriver: CloudDriverService,
  private val orcaService: OrcaService,
  private val igorService: ArtifactService,
  private val imageService: ImageService,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResolvableResourceHandler<ImageSpec, Image> {

  override val apiVersion = SPINNAKER_API_V1.subApi("bakery")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "image",
    "images"
  ) to ImageSpec::class.java

  override fun generateName(spec: ImageSpec): ResourceName =
    ResourceName("bakery:image:${spec.artifactName}")

  override suspend fun desired(resource: Resource<ImageSpec>): Image =
    with(resource) {
      val artifact = DeliveryArtifact(spec.artifactName, DEB)
      val latestVersion = artifact.findLatestVersion()
      val baseImage = baseImageCache.getBaseImage(spec.baseOs, spec.baseLabel)
      val baseAmi = findBaseAmi(baseImage)
      Image(
        baseAmiVersion = baseAmi,
        appVersion = latestVersion,
        regions = spec.regions
      )
    }

  override suspend fun current(resource: Resource<ImageSpec>): Image? =
    with(resource) {
      imageService.getLatestImage(spec.artifactName, "test")
    }

  private fun DeliveryArtifact.findLatestVersion(): String =
    artifactRepository
      .versions(this)
      .firstOrNull() ?: throw NoKnownArtifactVersions(this)

  override suspend fun upsert(
    resource: Resource<ImageSpec>,
    resourceDiff: ResourceDiff<Image>
  ): List<TaskRef> {
    val (_, application, version) = resourceDiff.desired.appVersion.let { appVersion ->
      Regex("([\\w_]+)-(.+)")
        .find(appVersion)
        ?.groupValues
        ?: throw IllegalStateException("Could not parse app version $appVersion")
    }
    val artifact = igorService.getArtifact(application, version)
    log.info("baking new image for {}", resource.spec.artifactName)
    val taskRef = orcaService.orchestrate(
      OrchestrationRequest(
        name = "Bake ${resourceDiff.desired.appVersion}",
        application = "keel", // TODO: revisit if/when we have a way to tie resources to applications
        description = "Bake ${resourceDiff.desired.appVersion}",
        job = listOf(
          Job(
            "bake",
            mapOf(
              "amiSuffix" to "",
              "baseOs" to resource.spec.baseOs,
              "baseLabel" to resource.spec.baseLabel.name.toLowerCase(),
              "cloudProviderType" to "aws",
              "package" to resource.spec.artifactName,
              "regions" to resource.spec.regions,
              "storeType" to resource.spec.storeType.name.toLowerCase(),
              "user" to "keel",
              "vmType" to "hvm"
            )
          )
        ),
        trigger = OrchestrationTrigger(
          correlationId = resource.name.toString(),
          artifacts = listOf(artifact)
        )
      )
    )
    return listOf(TaskRef(taskRef.ref)) // TODO: wow, this is ugly
  }

  override suspend fun delete(resource: Resource<ImageSpec>) {
    TODO("not implemented")
  }

  override suspend fun actuationInProgress(name: ResourceName) =
    orcaService
      .getCorrelatedExecutions(name.value)
      .isNotEmpty()

  private suspend fun findBaseAmi(baseImage: String): String {
    return cloudDriver.namedImages(baseImage, "test")
      .lastOrNull()
      ?.let { namedImage ->
        val tags = namedImage
          .tagsByImageId
          .values
          .first { it?.containsKey("base_ami_version") ?: false }
        if (tags != null) {
          tags.getValue("base_ami_version")!!
        } else {
          null
        }
      } ?: throw BaseAmiNotFound(baseImage)
  }

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
}

class BaseAmiNotFound(baseImage: String) : RuntimeException("Could not find a base AMI for base image $baseImage")
