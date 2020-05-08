package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.core.api.ClusterDeployStrategy
import com.netflix.spinnaker.keel.core.api.Highlander
import com.netflix.spinnaker.keel.core.api.RedBlack
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Provides common logic for multiple cloud plugins to export aspects of compute clusters.
 */
@Component
class ClusterExportHelper(
  private val cloudDriverService: CloudDriverService,
  private val orcaService: OrcaService
) {
  val log by lazy { LoggerFactory.getLogger(javaClass) }

  /**
   * Retrieve server group entity tags from clouddriver to identity the "source" of the server group (pipeline or
   * orchestration), then retrieve execution details from orca to determine the deployment strategy used for that
   * server group.
   */
  suspend fun discoverDeploymentStrategy(
    cloudProvider: String,
    account: String,
    application: String,
    serverGroupName: String
  ): ClusterDeployStrategy? {
    return kotlinx.coroutines.coroutineScope {
      val entityTags = async {
        log.debug("Looking for entity tags on server group $serverGroupName in application $application, " +
          "account $account in search of pipeline/task correlation.")
        cloudDriverService.getEntityTags(
          cloudProvider = "aws",
          account = account,
          application = application,
          entityType = "servergroup",
          entityId = serverGroupName
        )
      }.await()

      if (entityTags.isEmpty()) {
        log.warn("Unable to find entity tags for server group $serverGroupName in application $application, " +
          "account $account.")
        return@coroutineScope null
      }

      val spinnakerMetadata = entityTags.first().tags
        .find { it.name == "spinnaker:metadata" }
        ?.let { it.value as? Map<*, *> }

      if (spinnakerMetadata == null ||
        spinnakerMetadata["executionType"] == null ||
        spinnakerMetadata["executionId"] == null
      ) {
        log.warn("Unable to find Spinnaker metadata for server group $serverGroupName in application $application, " +
          "account $account in entity tags.")
        return@coroutineScope null
      }

      val executionType = spinnakerMetadata["executionType"].toString()
      val executionId = spinnakerMetadata["executionId"].toString()
      val execution = async {
        when (executionType) {
          "orchestration" -> orcaService.getOrchestrationExecution(executionId)
          "pipeline" -> orcaService.getPipelineExecution(executionId)
          else -> null
        }
      }.await()

      if (execution == null) {
        log.error("Unsupported execution type $executionType in Spinnaker metadata. Unable to determine deployment " +
          "strategy for server group $serverGroupName in application $application, account $account.")
        return@coroutineScope null
      }

      val context = if (executionType == "pipeline") {
        // TODO: or clone?
        execution.getDeployStageContext()
      } else { // orchestration (i.e. a task)
        // TODO: or cloneServerGroup?
        execution.getTaskContext("createServerGroup")
      }

      when (val strategy = context?.get("strategy")) {
        "redblack" -> {
          try {
            RedBlack.fromOrcaStageContext(context)
          } catch (e: ClassCastException) {
            log.error("Could not convert strategy to redblack, context is {}", context)
            null
          }
        }
        "highlander" -> Highlander
        null -> null.also {
          log.error("Deployment strategy information not found for server group $serverGroupName " +
            "in application $application, account $account")
        }
        else -> null.also {
          log.error("Deployment strategy $strategy associated with server group $serverGroupName " +
            "in application $application, account $account is not supported. " +
            "Only redblack and highlander are supported at this time.")
        }
      }
    }
  }

  private fun ExecutionDetailResponse.getDeployStageContext() =
    stages
      ?.find { stage -> stage["type"] == "deploy" }
      ?.let { stage -> stage["context"] }
      ?.let { context -> context as? OrcaExecutionStage }
      ?.let { context ->
        (context["clusters"] as? List<OrcaExecutionStage>)?.first()
      }

  private fun ExecutionDetailResponse.getTaskContext(taskType: String) =
    execution.stages
      ?.find { stage -> stage["type"] == taskType }
      ?.let { stage -> stage["context"] }
      ?.let { context -> context as? Map<String, Any> }
}
