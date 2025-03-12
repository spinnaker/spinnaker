/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.keel.task

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.kork.annotations.VisibleForTesting
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.SourceCodeTrigger
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.igor.ScmService
import com.netflix.spinnaker.orca.keel.model.Commit
import com.netflix.spinnaker.orca.keel.model.GitMetadata
import com.netflix.spinnaker.orca.keel.model.Repo
import com.netflix.spinnaker.orca.keel.model.TriggerWithGitData
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Task that retrieves a Managed Delivery config manifest from source control via igor, then publishes it to keel,
 * to support git-based workflows.
 */
@Component
class ImportDeliveryConfigTask
constructor(
  private val keelService: KeelService,
  private val scmService: ScmService,
  private val objectMapper: ObjectMapper
) : RetryableTask {
  private val log = LoggerFactory.getLogger(javaClass)

  override fun execute(stage: StageExecution): TaskResult {
    val context = objectMapper.convertValue<ImportDeliveryConfigContext>(stage.context)
    val trigger = stage.execution.trigger
    val user = trigger.user ?: "anonymous"
    val manifestLocation = processDeliveryConfigLocation(trigger, context)

    return try {
      log.debug("Retrieving keel manifest at $manifestLocation")
      val deliveryConfig = scmService.getDeliveryConfigManifest(
        context.repoType, context.projectKey, context.repositorySlug, context.directory, context.manifest, context.ref
      )

      val metadata: MutableMap<String, Any?> = objectMapper.convertValue(deliveryConfig.getOrDefault("metadata", emptyMap<String, Any?>()))
      val gitMetadata = processTriggerGitInfo(trigger, stage)
      if (gitMetadata != null) {
        metadata["gitMetadata"] = gitMetadata
        deliveryConfig["metadata"] = metadata
      }

      log.debug("Publishing manifest ${context.manifest} to keel on behalf of $user")
      keelService.publishDeliveryConfig(deliveryConfig)

      TaskResult.builder(ExecutionStatus.SUCCEEDED).context(emptyMap<String, Any?>()).build()
    } catch (e: SpinnakerServerException) {
      handleRetryableFailures(e, context)
    } catch (e: Exception) {
      log.error("Unexpected exception while executing {}, aborting.", javaClass.simpleName, e)
      buildError(e.message ?: "Unknown error (${e.javaClass.simpleName})")
    }
  }

  private fun processTriggerGitInfo(trigger: Trigger, stage: StageExecution): GitMetadata? {
    try {
      val gitTrigger: TriggerWithGitData = objectMapper.convertValue(trigger)
      with(gitTrigger) {
        return GitMetadata(
          commit = hash,
          author = payload.causedBy.email,
          project = project,
          branch = branch,
          repo = Repo(
            name = payload.source.repoName,
          ),
          commitInfo = Commit(
            sha = payload.source.sha,
            link = payload.source.url,
            message = payload.source.message
          ),
          pullRequest = if (payload.pullRequest.number != "-1") payload.pullRequest else null
        )
      }

    } catch (e: Exception) {
      log.debug("Can't pull enough git information out of trigger $trigger for execution ${stage.execution.id} and stage ${stage.id}: {}", e)
    }
    return null
  }

  /**
   * Process the trigger and context data to make sure we can find the delivery config file.
   *
   * @throws InvalidRequestException if there's not enough information to locate the file.
   */
  private fun processDeliveryConfigLocation(trigger: Trigger, context: ImportDeliveryConfigContext): String {
    if (trigger is SourceCodeTrigger) {
      // if the pipeline has a source code trigger (git, etc.), infer what context we can from the trigger
      if (context.ref == null) {
        context.ref = trigger.hash
        log.debug("Inferred context.ref from trigger: ${context.ref}")
      }
      if (context.repoType == null) {
        context.repoType = trigger.source
        log.debug("Inferred context.scmType from trigger: ${context.repoType}")
      }
      if (context.projectKey == null) {
        context.projectKey = trigger.project
        log.debug("Inferred context.project from trigger: ${context.projectKey}")
      }
      if (context.repositorySlug == null) {
        context.repositorySlug = trigger.slug
        log.debug("Inferred context.repository from trigger: ${context.repositorySlug}")
      }
    } else {
      // otherwise, apply defaults where possible, or fail if there's not enough information in the context
      if (context.ref == null) {
        context.ref = "refs/heads/master"
      }
      if (context.repoType == null || context.projectKey == null || context.repositorySlug == null) {
        throw InvalidRequestException("repoType, projectKey and repositorySlug are required fields in the stage if there's no git trigger.")
      }
    }

    if (context.manifest.isNullOrBlank()) {
      context.manifest = "spinnaker.yml"
    }

    // this is just a friend URI-like string to refer to the delivery config location in logs
    return "${context.repoType}://${context.projectKey}/${context.repositorySlug}/<manifestBaseDir>/${context.directory
      ?: ""}/${context.manifest}@${context.ref}"
  }

  /*
  * Handle (potentially) all Spinnaker*Exception. Smart casts to the respective type on Http error and/or Network error.
  * @return default error message on non-http and non-network errors.
  * */
  private fun handleRetryableFailures(error: SpinnakerServerException, context: ImportDeliveryConfigContext): TaskResult {
    return when {
      error is SpinnakerNetworkException -> {
        // retry if unable to connect
        buildRetry(
          context,
          "Network error talking to downstream service, attempt ${context.attempt} of ${context.maxRetries}: ${error.networkErrorMessage}"
        )
      }
      error is SpinnakerHttpException -> {
        handleRetryableFailures(error, context)
      } else -> {
        buildRetry(
          context,
          "Server error talking to downstream service, attempt ${context.attempt} of ${context.maxRetries}: ${error.serverErrorMessage}"
        )
      }
    }
  }

  /**
   * Handle (potentially) retryable failures by looking at the HTTP status code. A few 4xx errors
   * are handled as special cases to provide more friendly error messages to the UI.
   */
  @VisibleForTesting
  fun handleRetryableFailures(httpException: SpinnakerHttpException, context: ImportDeliveryConfigContext): TaskResult{
    return when {
      httpException.responseCode in 400..499 -> {
        val responseBody = httpException.responseBody
        // just give up on 4xx errors, which are unlikely to resolve with retries, but give users a hint about 401
        // errors from igor/scm, and attempt to parse keel errors (which are typically more informative)
        buildError(
          if (httpException.fromIgor && httpException.responseCode == 401) {
            UNAUTHORIZED_SCM_ACCESS_MESSAGE
          } else if (httpException.fromKeel && responseBody!=null && responseBody.isNotEmpty()) {
            // keel's errors should use the standard Spring format
            try {
              if (responseBody["timestamp"] !=null) {
                SpringHttpError(responseBody["error"] as String, responseBody["status"] as Int, responseBody["message"] as? String, Instant.ofEpochMilli(responseBody["timestamp"] as Long), responseBody["details"] as? Map<String, Any?>)
              } else {
                SpringHttpError(error = responseBody["error"] as String, status = responseBody["status"] as Int, message = responseBody["message"] as? String, details = responseBody["details"] as? Map<String, Any?>)
              }
            } catch (_: Exception) {
              "Non-retryable HTTP response ${httpException.responseCode} received from downstream service: ${httpException.httpErrorMessage}"
            }
          } else {
            "Non-retryable HTTP response ${httpException.responseCode} received from downstream service: ${httpException.httpErrorMessage}"
          }
        )
      }
      else -> {
        // retry on other status codes
        buildRetry(
          context,
          "Retryable HTTP response ${httpException.responseCode} received from downstream service: ${httpException.httpErrorMessage}"
        )
      }
    }
  }

  /**
   * Builds a [TaskResult] that indicates the task is still running, so that we will try again in the next execution loop.
   */
  private fun buildRetry(context: ImportDeliveryConfigContext, errorMessage: String): TaskResult {
    log.error("Handling retryable failure ${context.attempt} of ${context.maxRetries}: $errorMessage")
    context.errorFromLastAttempt = errorMessage
    context.incrementAttempt()

    return if (context.attempt > context.maxRetries!!) {
      val error = "Maximum number of retries exceeded (${context.maxRetries}). " +
        "The error from the last attempt was: $errorMessage"
      log.error("$error. Aborting.")
      TaskResult.builder(ExecutionStatus.TERMINAL).context(
        mapOf("error" to error, "errorFromLastAttempt" to errorMessage)
      ).build()
    } else {
      TaskResult.builder(ExecutionStatus.RUNNING).context(context.toMap()).build()
    }
  }

  /**
   * Builds a [TaskResult] that indicates the task has failed. If the error has the shape of a [SpringHttpError],
   * uses that format so the UI has better error information to display.
   */
  private fun buildError(error: Any): TaskResult {
    val normalizedError = if (error is SpringHttpError) {
      error
    } else {
      mapOf("message" to error.toString())
    }
    log.error(normalizedError.toString())
    return TaskResult.builder(ExecutionStatus.TERMINAL).context(mapOf("error" to normalizedError)).build()
  }

  override fun getBackoffPeriod() = TimeUnit.SECONDS.toMillis(30)

  override fun getTimeout() = TimeUnit.SECONDS.toMillis(180)

  val SpinnakerHttpException.httpErrorMessage: String
    get() {
      return "HTTP ${responseCode} ${url}: ${cause?.message ?: message}"
    }

  val SpinnakerNetworkException.networkErrorMessage: String
    get() {
      return "$message: ${cause?.message ?: ""}"
    }

  val SpinnakerServerException.serverErrorMessage: String
    get() {
      return "$message"
    }

  val SpinnakerServerException.fromIgor: Boolean
    get() {
      val parsedUrl = URL(url)
      return parsedUrl.host.contains("igor") || parsedUrl.port == 8085
    }

  val SpinnakerServerException.fromKeel: Boolean
    get() {
      val parsedUrl = URL(url)
      return parsedUrl.host.contains("keel") || parsedUrl.port == 8087
    }

  data class ImportDeliveryConfigContext(
    var repoType: String? = null,
    var projectKey: String? = null,
    var repositorySlug: String? = null,
    var directory: String? = null, // as in, the directory *under* whatever manifest base path is configured in igor (e.g. ".netflix")
    var manifest: String? = null,
    var ref: String? = null,
    var attempt: Int = 1,
    val maxRetries: Int? = MAX_RETRIES,
    var errorFromLastAttempt: String? = null
  )

  fun ImportDeliveryConfigContext.incrementAttempt() = this.also { attempt += 1 }
  fun ImportDeliveryConfigContext.toMap() = objectMapper.convertValue<Map<String, Any?>>(this)

  data class SpringHttpError(
    val error: String,
    val status: Int,
    val message: String? = error,
    val timestamp: Instant? = Instant.now(),
    val details: Map<String, Any?>? = null // this is keel-specific
  )

  companion object {
    const val MAX_RETRIES = 5
    const val UNAUTHORIZED_SCM_ACCESS_MESSAGE =
      "HTTP 401 response received while trying to read your delivery config file. " +
        "Spinnaker may be missing permissions in your source code repository to read the file."
  }
}
