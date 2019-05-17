package com.netflix.spinnaker.keel.bakery.resource

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.NoKnownArtifactVersions
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.events.TaskRef
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.plugin.ResolvableResourceHandler
import com.netflix.spinnaker.keel.plugin.ResolvedResource
import com.netflix.spinnaker.keel.plugin.ResourceDiff
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ImageHandler(
  override val objectMapper: ObjectMapper,
  private val artifactRepository: ArtifactRepository,
  private val baseImageCache: BaseImageCache,
  private val cloudDriver: CloudDriverService,
  private val orcaService: OrcaService,
  private val igorService: ArtifactService,
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

  override fun resolve(resource: Resource<ImageSpec>): ResolvedResource<Image> =
    with(resource) {
      val artifact = DeliveryArtifact(spec.artifactName, DEB)
      val latestVersion = artifactRepository
        .versions(artifact)
        .firstOrNull() ?: throw NoKnownArtifactVersions(artifact)
      runBlocking {
        ResolvedResource(
          desired = desired(resource.spec, latestVersion),
          current = current(resource.spec, latestVersion)
        )
      }
    }

  private suspend fun desired(spec: ImageSpec, version: String): Image {
    val baseImage = baseImageCache.getBaseImage(spec.baseOs, spec.baseLabel)
    val baseAmi = findBaseAmi(baseImage)
    return Image(
      baseAmiVersion = baseAmi,
      appVersion = version,
      regions = spec.regions
    )
  }

  private suspend fun current(spec: ImageSpec, version: String): Image? =
    cloudDriver.namedImages(version, "test")
      .await()
      .lastOrNull()
      ?.let { namedImage ->
        val tags = namedImage
          .tagsByImageId
          .values
          .first { it?.containsKey("base_ami_version") ?: false && it?.containsKey("appversion") ?: false }
        if (tags == null) {
          null
        } else {
          Image(
            tags.getValue("base_ami_version")!!,
            tags.getValue("appversion")!!.substringBefore('/'),
            namedImage.amis.keys
          )
        }
      }
      .also {
        log.debug("Latest image for {} version {} is {}", spec.artifactName, version, it)
      }

  override fun upsert(
    resource: Resource<ImageSpec>,
    resourceDiff: ResourceDiff<Image>
  ): List<TaskRef> {
    val taskRef = runBlocking {
      val (_, application, version) = resourceDiff.desired.appVersion.let { appVersion ->
        Regex("([\\w_]+)-(.+)")
          .find(appVersion)
          ?.groupValues
          ?: throw IllegalStateException("Could not parse app version $appVersion")
      }
      val artifact = igorService.getArtifact(application, version)
      log.info("baking new image for {}", resource.spec.artifactName)
      orcaService.orchestrate(
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
            correlationId = resource.metadata.name.toString(),
            artifacts = listOf(artifact.await())
          )
        )
      )
        .await()
    }
    return listOf(TaskRef(taskRef.ref)) // TODO: wow, this is ugly
  }

  override fun delete(resource: Resource<ImageSpec>) {
    TODO("not implemented")
  }

  override fun actuationInProgress(name: ResourceName) =
    runBlocking {
      orcaService.getCorrelatedExecutions(name.value).await()
    }.isNotEmpty()

  private suspend fun findBaseAmi(baseImage: String): String {
    return cloudDriver.namedImages(baseImage, "test")
      .await()
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

/**
 * The resolved representation of an [ImageSpec].
 */
data class Image(
  val baseAmiVersion: String,
  val appVersion: String,
  val regions: Set<String>
)

class BaseAmiNotFound(baseImage: String) : RuntimeException("Could not find a base AMI for base image $baseImage")
