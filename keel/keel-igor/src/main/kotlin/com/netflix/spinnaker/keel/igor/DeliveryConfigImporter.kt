package com.netflix.spinnaker.keel.igor

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.parseDeliveryConfig
import com.netflix.spinnaker.keel.scm.CodeEvent
import com.netflix.spinnaker.keel.scm.CommitCreatedEvent
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Provides functionality to import delivery config manifests from source control repositories (via igor).
 */
class DeliveryConfigImporter(
  private val scmService: ScmService,
  private val front50Cache: Front50Cache,
  private val yamlMapper: YAMLMapper,

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
    manifestPath: String?,
    ref: String,
    addMetadata: Boolean = true
  ): SubmittedDeliveryConfig {
    val manifestLocation = "$repoType://project:$projectKey/repo:$repoSlug/manifest:$manifestPath@$ref"

    log.debug("Retrieving delivery config from $manifestLocation")
    val rawDeliveryConfig = runBlocking {
      scmService.getDeliveryConfigManifest(repoType, projectKey, repoSlug, manifestPath ?: DEFAULT_MANIFEST_PATH, ref, true)
    }.manifest
    val submittedDeliveryConfig = yamlMapper.parseDeliveryConfig(rawDeliveryConfig)

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
  fun import(codeEvent: CodeEvent, manifestPath: String?): SubmittedDeliveryConfig {
    with(codeEvent) {
      return import(
        repoType,
        projectKey,
        repoSlug,
        manifestPath,
        commitHash ?: error("commit hash is missing in commit event $codeEvent")
      )
    }
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
        manifestPath = app.managedDelivery?.manifestPath,
        ref = "refs/heads/${getDefaultBranch(scmService)}",
        addMetadata = addMetadata
      )
    }
  }
}
