package com.netflix.spinnaker.keel.igor

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.scm.CommitCreatedEvent
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Provides functionality to import delivery config manifests from source control repositories (via igor).
 */
class DeliveryConfigImporter(
  private val jsonMapper: ObjectMapper,
  private val scmService: ScmService,
  private val front50Cache: Front50Cache
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(DeliveryConfigImporter::class.java) }
    const val DEFAULT_MANIFEST_PATH = "spinnaker.yml"
  }

  /**
   * Imports a delivery config from source control via igor.
   */
  fun import(
    repoType: String,
    projectKey: String,
    repoSlug: String,
    manifestPath: String,
    ref: String,
    addMetadata: Boolean = true
  ): SubmittedDeliveryConfig {
    val manifestLocation = "$repoType://project:$projectKey/repo:$repoSlug/manifest:$manifestPath@$ref"

    log.debug("Retrieving delivery config from $manifestLocation")
    val submittedDeliveryConfig = runBlocking {
      scmService.getDeliveryConfigManifest(repoType, projectKey, repoSlug, manifestPath, ref)
    }

    log.debug("Successfully retrieved delivery config from $manifestLocation.")
    return if (addMetadata) {
      submittedDeliveryConfig.copy(
        metadata = mapOf(
          "importedFrom" to
            mapOf(
              "repoType" to repoType,
              "projectKey" to projectKey,
              "repoSlug" to repoSlug,
              "manifestPath" to manifestPath,
              "ref" to ref
            )
        )
      )
    } else {
      submittedDeliveryConfig
    }
  }

  /**
   * Imports a delivery config from source control based on the details of the [CommitCreatedEvent].
   */
  fun import(commitEvent: CommitCreatedEvent, manifestPath: String = DEFAULT_MANIFEST_PATH) =
    with(commitEvent) {
      import(repoType, projectKey, repoSlug, manifestPath, commitHash)
    }


  /**
   * Imports a delivery config from source control at refs/heads/{defaultBranch},
   * based on the configuration of the [application].
   */
  fun import(application: String, addMetadata: Boolean = true): SubmittedDeliveryConfig {
    val app = runBlocking {
      front50Cache.applicationByName(application)
    }
    return with(app) {
      import(
        repoType = repoType ?: error("Missing SCM type in config for application $name"),
        projectKey = repoProjectKey ?: error("Missing SCM project in config for application $name"),
        repoSlug = repoSlug ?: error("Missing SCM repository in config for application $name"),
        manifestPath = DEFAULT_MANIFEST_PATH, // TODO: make configurable
        ref = "refs/heads/${defaultBranch}",
        addMetadata = addMetadata
      )
    }
  }

  private val Application.defaultBranch: String
    get() = runBlocking {
      scmService.getDefaultBranch(
        scmType = repoType ?: error("Missing SCM type in config for application $name"),
        projectKey = repoProjectKey ?: error("Missing SCM project in config for application $name"),
        repoSlug = repoSlug ?: error("Missing SCM repository in config for application $name")
      ).name
    }
}
