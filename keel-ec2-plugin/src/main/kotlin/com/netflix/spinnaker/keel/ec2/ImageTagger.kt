package com.netflix.spinnaker.keel.ec2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.CurrentImages
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * This class tags images after they've been verified.
 *
 * This is meant to provide a hook to transition from managed
 * delivery back to pipelines. Images will be tagged when they have
 * successfully been verified with `latest tested = true`. This allows
 * a pipeline to find the latest tested image and deploy that.
 *
 * This runs for all images that have passed verification.
 */
@Component
class ImageTagger(
  private val mapper: ObjectMapper,
  private val taskLauncher: TaskLauncher,
  private val actionRepository: ActionRepository,
  private val keelRepository: KeelRepository,
  private val springEnv: Environment,
  private val spectator: Registry
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val TAG_AMI_JOB_LAUNCHED = "keel.image.tag"

  private val shouldTagImages: Boolean
    get() = springEnv.getProperty("keel.image.tagging.enabled", Boolean::class.java, true)

  @EventListener(VerificationCompleted::class)
  fun onVerificationCompleted(event: VerificationCompleted) {
    if (event.status.failed() || !shouldTagImages) {
      return
    }

    val config = keelRepository.getDeliveryConfigForApplication(event.application)
    val verificationContext = ArtifactInEnvironmentContext(
      deliveryConfig = config,
      environmentName = event.environmentName,
      artifactReference = event.artifactReference,
      version = event.artifactVersion
    )

    if (!actionRepository.allPassed(verificationContext, ActionType.VERIFICATION)) {
      log.debug("All verifications have not passed for ${verificationContext.shortName()}, waiting to tag image")
      return
    }

    val imagesRaw: Any = event.metadata["images"] ?: emptyList<CurrentImages>()
    val images: List<CurrentImages> = try {
      mapper.convertValue(imagesRaw)
    } catch (e: IllegalArgumentException) {
      log.error("Malformed metadata in 'images' key: $imagesRaw")
      emptyList()
    }

    val jobs = images
      .filter { it.kind.group == "ec2" } // spinnaker only supports tagging amis
      .map { it.toJob(event.environmentName, event.verificationId) }

    jobs.forEach { job ->
      val names = job["imageNames"].toString()
      val task = runBlocking {
        taskLauncher.submitJob(
          user = DEFAULT_SERVICE_ACCOUNT,
          application = event.application,
          environmentName = event.environmentName,
          resourceId = null,
          notifications = emptySet(),
          description = "Automatically tagging image(s) as verified $names",
          correlationId = names,
          stages = listOf(job)
        )
      }
      log.debug("Launching task ${task.id} to tag image(s) $names")
      spectator.counter(
        TAG_AMI_JOB_LAUNCHED,
        listOf(BasicTag("application", event.application))
      ).increment()
    }
  }

  fun CurrentImages.toJob(env: String, verificationId: String): Map<String, Any?> =
    mapOf(
      "type" to "upsertImageTags",
      "imageNames" to images.map { it.imageName },
      "regions" to images.map { it.region }.toSet(),
      "tags" to mapOf(
        "latest tested" to true,
        env to "environment:passed"
      )
    )
}
