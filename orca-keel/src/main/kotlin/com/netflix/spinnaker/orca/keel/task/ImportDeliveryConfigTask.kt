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
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.orca.KeelService
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.SourceCodeTrigger
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger
import com.netflix.spinnaker.orca.igor.ScmService
import java.net.URL
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import retrofit.RetrofitError

/**
 * Task that retrieves a Managed Delivery config manifest from source control via igor, then publishes it to keel,
 * to support GitOps flows.
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
        context.repoType, context.projectKey, context.repositorySlug, context.directory, context.manifest, context.ref)

      log.debug("Publishing manifest ${context.manifest} to keel on behalf of $user")
      keelService.publishDeliveryConfig(deliveryConfig, user)

      TaskResult.builder(ExecutionStatus.SUCCEEDED).context(emptyMap<String, Any?>()).build()
    } catch (e: RetrofitError) {
      handleRetryableFailures(e, context)
    } catch (e: Exception) {
      log.error("Unexpected exception while executing {}, aborting.", javaClass.simpleName, e)
      buildError(e.message ?: "Unknown error (${e.javaClass.simpleName})")
    }
  }

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
        throw IllegalArgumentException("repoType, projectKey and repositorySlug are required fields in the stage if there's no git trigger.")
      }
    }

    // this is just a friend URI-like string to refer to the delivery config location in logs
    return "${context.repoType}://${context.projectKey}/${context.repositorySlug}/<manifestBaseDir>/${context.directory
      ?: ""}/${context.manifest}@${context.ref}"
  }

  private fun handleRetryableFailures(e: RetrofitError, context: ImportDeliveryConfigContext): TaskResult {
    return when {
      e.kind == RetrofitError.Kind.NETWORK -> {
        // retry if unable to connect
        buildRetry(context,
          "Network error talking to downstream service, attempt ${context.attempt} of ${context.maxRetries}: ${e.friendlyMessage}")
      }
      e.response?.status in 400..499 -> {
        val response = e.response!!
        // just give up on 4xx errors, which are unlikely to resolve with retries
        buildError(
          // ...but give users a hint about 401 errors from igor/scm
          if (response.status == 401 && e.fromIgor) {
            UNAUTHORIZED_SCM_ACCESS_MESSAGE
          } else if (response.status == 400 && e.fromKeel && response.body.length() > 0) {
            objectMapper.readValue<Map<String, Any?>>(response.body.`in`())
          } else {
            "Non-retryable HTTP response ${e.response?.status} received from downstream service: ${e.friendlyMessage}"
          }
        )
      }
      else -> {
        // retry on other status codes
        buildRetry(context,
          "Retryable HTTP response ${e.response?.status} received from downstream service: ${e.friendlyMessage}")
      }
    }
  }

  private fun buildRetry(context: ImportDeliveryConfigContext, errorMessage: String): TaskResult {
    log.error("Handling retry failure ${context.attempt} of ${context.maxRetries}: $errorMessage")
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

  private fun buildError(error: Any): TaskResult {
    val normalizedError = if (error is Map<*, *>) {
      error["error"] ?: error
    } else {
      error.toString()
    }
    log.error(normalizedError.toString())
    return TaskResult.builder(ExecutionStatus.TERMINAL).context(mapOf("error" to normalizedError)).build()
  }

  override fun getBackoffPeriod() = TimeUnit.SECONDS.toMillis(30)

  override fun getTimeout() = TimeUnit.SECONDS.toMillis(180)

  val RetrofitError.friendlyMessage: String
    get() = if (kind == RetrofitError.Kind.HTTP) {
      "HTTP ${response.status} ${response.url}: ${cause?.message ?: message}"
    } else {
      "$message: ${cause?.message ?: ""}"
    }

  val RetrofitError.fromIgor: Boolean
    get() {
      val parsedUrl = URL(url)
      return parsedUrl.host.contains("igor") || parsedUrl.port == 8085
    }

  val RetrofitError.fromKeel: Boolean
    get() {
      val parsedUrl = URL(url)
      return parsedUrl.host.contains("keel") || parsedUrl.port == 8087
    }

  data class ImportDeliveryConfigContext(
    var repoType: String? = null,
    var projectKey: String? = null,
    var repositorySlug: String? = null,
    var directory: String? = null, // as in, the directory *under* whatever manifest base path is configured in igor (e.g. ".netflix")
    var manifest: String? = "spinnaker.yml",
    var ref: String? = null,
    var attempt: Int = 1,
    val maxRetries: Int? = MAX_RETRIES,
    var errorFromLastAttempt: String? = null
  )

  fun ImportDeliveryConfigContext.incrementAttempt() = this.also { attempt += 1 }
  fun ImportDeliveryConfigContext.toMap() = objectMapper.convertValue<Map<String, Any?>>(this)

  companion object {
    const val MAX_RETRIES = 5
    const val UNAUTHORIZED_SCM_ACCESS_MESSAGE =
      "HTTP 401 response received while trying to read your delivery config file. " +
      "Spinnaker may be missing permissions in your source code repository to read the file."
  }
}
