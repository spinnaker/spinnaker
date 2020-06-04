package com.netflix.spinnaker.keel.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.ScmService
import com.netflix.spinnaker.keel.core.api.SubmittedDeliveryConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Provides functionality to import delivery config manifests from source control repositories (via igor).
 */
@Component
class DeliveryConfigImporter(
  private val jsonMapper: ObjectMapper,
  private val scmService: ScmService
) {
  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Imports a delivery config from source control via igor.
   */
  fun import(
    repoType: String,
    projectKey: String,
    repoSlug: String,
    manifestPath: String,
    ref: String
  ): SubmittedDeliveryConfig {
    val manifestLocation = "$repoType://project:$projectKey/repo:$repoSlug/manifest:$manifestPath@$ref"

    log.debug("Retrieving delivery config from $manifestLocation")
    val submittedDeliveryConfig = runBlocking {
      scmService.getDeliveryConfigManifest(repoType, projectKey, repoSlug, manifestPath, ref)
    }

    log.debug("Successfully retrieved delivery config from $manifestLocation.")
    return submittedDeliveryConfig.copy(
      metadata = mapOf("importedFrom" to
        mapOf(
          "repoType" to repoType,
          "projectKey" to projectKey,
          "repoSlug" to repoSlug,
          "manifestPath" to manifestPath,
          "ref" to ref
        )
      )
    )
  }
}
