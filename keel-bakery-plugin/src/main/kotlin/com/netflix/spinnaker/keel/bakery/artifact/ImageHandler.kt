package com.netflix.spinnaker.keel.bakery.artifact

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.actuation.ArtifactHandler
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.getLatestNamedImages
import com.netflix.spinnaker.keel.clouddriver.model.baseImageName
import com.netflix.spinnaker.keel.core.NoKnownArtifactVersions
import com.netflix.spinnaker.keel.igor.artifact.ArtifactService
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope.PRE_DEPLOYMENT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.NOT_STARTED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.keel.model.OrcaJob
import com.netflix.spinnaker.keel.parseAppVersion
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchArtifactException
import com.netflix.spinnaker.keel.persistence.PausedRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactCheckSkipped
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment

class ImageHandler(
  private val repository: KeelRepository,
  private val baseImageCache: BaseImageCache,
  private val bakedImageRepository: BakedImageRepository,
  private val igorService: ArtifactService,
  private val imageService: ImageService,
  private val publisher: ApplicationEventPublisher,
  private val taskLauncher: TaskLauncher,
  private val defaultCredentials: BakeCredentials,
  private val pausedRepository: PausedRepository,
  private val springEnv: Environment
) : ArtifactHandler {

  override suspend fun handle(artifact: DeliveryArtifact) {
    if (artifact is DebianArtifact && !artifact.isPaused()) {
      val desiredAppVersion = try {
        artifact.findLatestArtifactVersion()
      } catch (e: NoKnownArtifactVersions) {
        log.debug(e.message)
        return
      }

      if (taskLauncher.correlatedTasksRunning(artifact.correlationId(desiredAppVersion))) {
        publisher.publishEvent(
          ArtifactCheckSkipped(artifact.type, artifact.name, "ActuationInProgress")
        )
      } else {
        val desiredBaseAmiName = artifact.findLatestBaseAmiName()

        val byArtifactVersion =
          artifact.wasPreviouslyBakedWith(desiredAppVersion, desiredBaseAmiName)
        if (byArtifactVersion) {
          return
        }

        val images = artifact.findLatestAmi(desiredAppVersion)
        val imagesWithOlderBaseImages = images.filterValues { it.baseImageName != desiredBaseAmiName }
        val missingRegions = artifact.vmOptions.regions - images.keys
        when {
          images.isEmpty() -> {
            log.info("No AMI found for {}", desiredAppVersion)
            launchBake(artifact, desiredAppVersion)
          }
          imagesWithOlderBaseImages.isNotEmpty() && !artifact.vmOptions.ignoreBaseUpdates -> {
            log.info("AMIs for {} are outdated, rebaking…", desiredAppVersion)
            launchBake(
              artifact,
              desiredAppVersion,
              description = "Bake $desiredAppVersion due to a new base image: $desiredBaseAmiName"
            )
          }
          missingRegions.isNotEmpty() -> {
            log.warn("Detected missing regions for ${desiredAppVersion}: ${missingRegions.joinToString()}")
            publisher.publishEvent(ImageRegionMismatchDetected(desiredAppVersion, desiredBaseAmiName, images.keys, artifact.vmOptions.regions))
            launchBake(
              artifact,
              desiredAppVersion,
              regions = missingRegions,
              description = "Bake $desiredAppVersion due to missing regions: ${missingRegions.joinToString()}"
            )
          }
          else -> {
            log.debug("Image for {} already exists with app version {} and base image {} in regions {}", artifact.name, desiredAppVersion, desiredBaseAmiName, artifact.vmOptions.regions.joinToString())
          }
        }
      }
    }
  }

  private fun DeliveryArtifact.isPaused(): Boolean =
    if (deliveryConfigName == null) {
      false
    } else {
      val config = repository.getDeliveryConfig(deliveryConfigName!!)
      pausedRepository.applicationPaused(config.application)
    }

  private fun DebianArtifact.wasPreviouslyBakedWith(
    desiredAppVersion: String,
    desiredBaseAmiName: String
  ) =
    bakedImageRepository
      .getByArtifactVersion(desiredAppVersion, this)
      ?.let {
        it.baseAmiName == desiredBaseAmiName && it.amiIdsByRegion.keys.containsAll(vmOptions.regions)
      } ?: false

  private suspend fun DebianArtifact.findLatestAmi(desiredArtifactVersion: String) =
    imageService.getLatestNamedImages(
      appVersion = AppVersion.parseName(desiredArtifactVersion),
      account = defaultImageAccount,
      regions = vmOptions.regions,
      baseOs = vmOptions.baseOs
    )

  private val defaultImageAccount: String
    get() = springEnv.getProperty("images.default-account", String::class.java, "test")

  private fun DebianArtifact.findLatestBaseAmiName() =
    baseImageCache.getBaseAmiName(vmOptions.baseOs, vmOptions.baseLabel)

  /**
   * First checks our repo, and if a version isn't found checks igor.
   */
  private suspend fun DebianArtifact.findLatestArtifactVersion(): String {
    try {
      val knownVersion = repository
        .artifactVersions(this, 1)
        .firstOrNull()
      if (knownVersion != null) {
        log.debug("Latest known version of $name = ${knownVersion.version}")
        return knownVersion.version
      }
    } catch (e: NoSuchArtifactException) {
      log.debug("Latest known version of $name = null")
      if (!repository.isRegistered(name, type)) {
        // we clearly care about this artifact, let's register it.
        repository.register(this)
        publisher.publishEvent(ArtifactRegisteredEvent(this))
      }
    }

    // even though the artifact isn't registered we should grab the latest version to use
    val versions = igorService
      .getVersions(name, statuses.map { it.toString() }, DEBIAN)
    log.debug("Finding latest version of $name: versions igor knows about = $versions")
    return versions
      .firstOrNull()
      ?.let {
        val version = "$name-$it"
        log.debug("Finding latest version of $name, choosing = $version")
        version
      } ?: throw NoKnownArtifactVersions(this)
  }

  private suspend fun launchBake(
    artifact: DebianArtifact,
    desiredVersion: String,
    regions: Set<String> = artifact.vmOptions.regions,
    description: String = "Bake $desiredVersion"
  ): List<Task> {
    // TODO: Frigga and Rocket version parsing are not aligned. We should consolidate.
    val appVersion = desiredVersion.parseAppVersion()
    val packageName = appVersion.packageName
    val version = desiredVersion.substringAfter("$packageName-")
    val fullArtifact = repository.getArtifactVersion(
      artifact,
      desiredVersion,
      null
    )
    val arch = fullArtifact?.metadata?.get("arch") ?: "all"
    val artifactRef = "/${packageName}_${version}_$arch.deb"
    val artifactPayload = mapOf(
      "type" to "DEB",
      "customKind" to false,
      "name" to artifact.name,
      "version" to version,
      "location" to "rocket",
      "reference" to artifactRef,
      "metadata" to emptyMap<String, Any>(),
      "provenance" to "n/a"
    )

    log.info("baking new image for {}", desiredVersion)

    val (serviceAccount, application) = artifact.taskAuthenticationDetails

    try {
      val taskRef = taskLauncher.submitJob(
        user = serviceAccount,
        application = application,
        environmentName = null,
        resourceId = null,
        notifications = emptySet(),
        description = description,
        correlationId = artifact.correlationId(desiredVersion),
        stages = listOf(
          OrcaJob(
            "bake",
            mapOf(
              "amiSuffix" to "",
              "baseOs" to artifact.vmOptions.baseOs,
              "baseLabel" to artifact.vmOptions.baseLabel.name.toLowerCase(),
              "cloudProviderType" to "aws",
              "package" to artifactRef.substringAfterLast("/"),
              "regions" to regions,
              "storeType" to artifact.vmOptions.storeType.name.toLowerCase(),
              "vmType" to "hvm"
            )
          )
        ),
        artifacts = listOf(artifactPayload),
      )
      publisher.publishEvent(BakeLaunched(desiredVersion))
      publisher.publishEvent(LifecycleEvent(
        scope = PRE_DEPLOYMENT,
        deliveryConfigName = checkNotNull(artifact.deliveryConfigName),
        artifactReference = artifact.reference,
        artifactVersion = desiredVersion,
        type = BAKE,
        id = "bake-$desiredVersion",
        status = NOT_STARTED,
        text = "Launching bake for $version",
        link = taskRef.id,
        startMonitoring = true
      ))
      return listOf(Task(id = taskRef.id, name = description))
    } catch (e: Exception) {
      log.error("Error launching bake for: $description")
      return emptyList()
    }
  }

  private val DebianArtifact.taskAuthenticationDetails: BakeCredentials
    get() = deliveryConfigName?.let {
      repository.getDeliveryConfig(it).run {
        BakeCredentials(serviceAccount, application)
      }
    } ?: defaultCredentials

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

/**
 * Use the version in the correlation id so that we can bake for multiple versions at once
 */
internal fun DebianArtifact.correlationId(version: String): String =
  "bake:$name:$version"

data class BakeCredentials(
  val serviceAccount: String,
  val application: String
)

data class ImageRegionMismatchDetected(val appVersion: String, val baseAmiName: String, val foundRegions: Set<String>, val desiredRegions: Set<String>)
data class RecurrentBakeDetected(val appVersion: String, val baseAmiVersion: String)
data class BakeLaunched(val appVersion: String)
