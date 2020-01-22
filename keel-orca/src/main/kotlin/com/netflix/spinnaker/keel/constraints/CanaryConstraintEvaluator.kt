package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.CanaryConstraint
import com.netflix.spinnaker.keel.api.CanaryConstraintAttributes
import com.netflix.spinnaker.keel.api.CanaryStatus
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.RegionalExecutionId
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import java.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * An environment promotion constraint that deploys control (baseline) and experiment (canary)
 * clusters which are evaluated by Kayenta using a constraint-provided canary config.
 *
 * When the constraint specifies a multi-region canary, the Kayenta ACA must pass in all
 * regions for promotion to be allowed. Kayenta false positives can be overridden to
 * allow promotion via the `DeliveryConfigController.updateConstraintStatus` REST method.
 *
 * Example constraint:
 *
 * constraints:
 *  - type: canary
 *    canaryConfigId: b11b739d-7b1d-47c7-8430-d8ddffafb645 // Will later support named configs
 *    beginAnalysisAfter: PT3M
 *    canaryAnalysisInterval: PT10M
 *    lifetime: PT30M
 *    marginalScore: 50
 *    passScore: 90
 *    capacity: 1
 *    source:
 *      account: test
 *      cloudProvider: aws
 *      cluster: fnord-test-c
 *    regions:
 *      - us-east-1
 *      - us-west-2
 *      - us-west-1
 *
 * Optional parameters:
 *    // For a multi-region canary, this many regions must pass for the constraint to pass.
 *    // If unset or set to 0, all regions must pass.
 *    minSuccessfulRegions: 2
 *    // When a region fails (or regional failures violate minSuccessfulRegions), the default
 *    // behavior is to immediate cancel any still running regions. If set to false, running
 *    // regions are left to complete naturally.
 *    failureCancelsRunningRegions: false
 */
@Component
class CanaryConstraintEvaluator(
  private val handlers: List<CanaryConstraintDeployHandler>,
  private val orcaService: OrcaService,
  override val deliveryConfigRepository: DeliveryConfigRepository,
  private val clock: Clock
) : StatefulConstraintEvaluator<CanaryConstraint>() {
  override val constraintType = CanaryConstraint::class.java
  private val log by lazy { LoggerFactory.getLogger(javaClass) }
  private val correlatedMessagePrefix = "Correlated canary tasks found"

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: CanaryConstraint,
    state: ConstraintState
  ): Boolean {
    val deployHandler = handlers.firstOrNull {
      it.supportedClouds.contains(constraint.source.cloudProvider)
    } ?: error("No canary constraint deploy handler found for ${constraint.source.cloudProvider}")

    val judge = "canary:${deliveryConfig.application}:${targetEnvironment.name}:${constraint.canaryConfigId}"
    var attributes = state.attributes as CanaryConstraintAttributes? ?: CanaryConstraintAttributes()
    val status = runBlocking {
      canaryStatus(attributes)
    }

    if (status.numFailed() > constraint.allowedFailures) {
      val stillRunning = status.getRunning()
      if (stillRunning.isNotEmpty() && constraint.failureCancelsRunningRegions) {
        runBlocking {
          stillRunning.forEach {
            log.warn("Cancelling still running canary $judge:${it.region} as at least one region has failed.")
            orcaService.cancelOrchestration(it.executionId)
          }
        }
      }
      deliveryConfigRepository.storeConstraintState(
        state.copy(
          status = ConstraintStatus.FAIL,
          judgedBy = judge,
          judgedAt = clock.instant(),
          comment = "Canary failed: ${status.summary()}"))

      return false
    }

    if (status.passed(constraint)) {
      deliveryConfigRepository.storeConstraintState(
        state.copy(
          status = ConstraintStatus.PASS,
          judgedBy = judge,
          judgedAt = clock.instant(),
          comment = "Canary passed: ${status.summary()}"))

      return true
    }

    val regionsToTrigger = shouldTrigger(constraint, attributes)
    val regionsWithCorrelatedExecutions = runBlocking {
      constraint.regionsWithCorrelatedExecutions(judge)
    }
    val unknownExecutions = regionsWithCorrelatedExecutions.filter {
      regionsToTrigger.contains(it.key)
    }
      .toSortedMap()

    if (unknownExecutions.isNotEmpty() && attributes.executions.isEmpty()) {
      val message = "$correlatedMessagePrefix ($unknownExecutions), " +
        "another artifact version may still be under evaluation."

      if (state.comment != message) {
        deliveryConfigRepository.storeConstraintState(state.copy(comment = message))
      }

      return false
    }

    if (regionsToTrigger.isEmpty()) {
      if (!status.anyRunning()) {
        deliveryConfigRepository.storeConstraintState(
          state.copy(
            status = ConstraintStatus.FAIL,
            judgedBy = judge,
            judgedAt = clock.instant(),
            attributes = attributes.copy(status = status),
            comment = "Failure encountered launching canaries"))
      } else if (attributes.status != status) {
        deliveryConfigRepository.storeConstraintState(
          state.copy(
            attributes = attributes.copy(status = status)))
      }

      return false
    }

    attributes = attributes.copy(startAttempt = attributes.startAttempt + 1)

    val tasks = runBlocking {
      deployHandler.deployCanary(
        constraint = constraint,
        version = version,
        deliveryConfig = deliveryConfig,
        targetEnvironment = targetEnvironment,
        regions = regionsToTrigger)
    }

    attributes = attributes.copy(
      executions = tasks.map { (region, task) ->
        RegionalExecutionId(region, task.id)
      }
        .toSet())

    deliveryConfigRepository.storeConstraintState(
      state.copy(
        attributes = attributes,
        comment = "Running canaries in ${attributes.launchedRegions}"))

    return false
  }

  @Suppress("UNCHECKED_CAST")
  private suspend fun canaryStatus(attributes: CanaryConstraintAttributes?): Set<CanaryStatus> =
    coroutineScope {
      if (attributes == null || attributes.executions.isNullOrEmpty()) {
        return@coroutineScope emptySet<CanaryStatus>()
      }

      return@coroutineScope attributes.executions
        .associate {
          it.region to async {
            orcaService
              .getOrchestrationExecution(it.executionId)
          }
        }
        .mapValues { it.value.await() }
        .map { (region, detail) ->
          val context = detail.execution.stages.canaryContext()

          CanaryStatus(
            executionId = detail.id,
            region = region,
            executionStatus = detail.status.toString(),
            scores = context.getOrDefault("canaryScores", emptyList<Double>()) as List<Double>,
            scoreMessage = context["canaryScoreMessage"] as String?
          )
        }
        .toSet()
    }

  private fun Set<CanaryStatus>.summary(): Map<String, Any> =
    associate { status ->
      status.region to mutableMapOf(
        "executionStatus" to status.executionStatus
      ).also {
        if (status.scoreMessage != null) {
          it["message"] = status.scoreMessage!!
        }
      }
    }

  private fun Set<CanaryStatus>.passed(constraint: CanaryConstraint): Boolean {
    val passed = filter {
      OrcaExecutionStatus.valueOf(it.executionStatus).isSuccess()
    }
      .size

    val failed = filter {
      OrcaExecutionStatus.valueOf(it.executionStatus).isFailure()
    }
      .size

    return passed + failed == size &&
      failed <= constraint.allowedFailures &&
      regions() == constraint.regions
  }

  private fun Set<CanaryStatus>.numFailed(): Int =
    filter {
      OrcaExecutionStatus.valueOf(it.executionStatus).isFailure()
    }
      .size

  private fun Set<CanaryStatus>.anyRunning(): Boolean =
    isNotEmpty() && getRunning().isNotEmpty()

  private fun Set<CanaryStatus>.getRunning(): Set<CanaryStatus> =
    filter {
      OrcaExecutionStatus.valueOf(it.executionStatus).isIncomplete()
    }
      .toSet()

  private fun Set<CanaryStatus>.regions(): Set<String> =
    map { it.region }
      .toSet()

  suspend fun CanaryConstraint.regionsWithCorrelatedExecutions(prefix: String): Map<String, String> =
    coroutineScope {
      regions.associateWith { region ->
        async {
          orcaService.getCorrelatedExecutions("$prefix:$region")
        }
      }
        .mapValues { it.value.await() }
        .filter { it.value.isNotEmpty() }
        .mapValues { it.value.first() }
    }

  /**
   * @return Set of regions (as strings) that still need a canary deploy.
   *
   * Supports triggering a subset of regions specified in the constraint.
   * If a constraint specifies 2 regions and on the first eval, Orca
   * successfully receives the task submission for one region, but the
   * second fails due to a network issue, the second constraint eval
   * will only attempt to trigger for the failed region.
   */
  private fun shouldTrigger(
    constraint: CanaryConstraint,
    attributes: CanaryConstraintAttributes?
  ): Set<String> =
    when {
      attributes == null -> constraint.regions
      constraint.regions.size == attributes.executions.size -> emptySet()
      attributes.startAttempt >= 3 -> emptySet()
      else -> constraint.regions - attributes.executions
        .map { it.region }
        .toSet()
    }

  private val CanaryConstraintAttributes.launchedRegions: Set<String>
    get() = executions
      .map { it.region }
      .toSet()

  private fun List<Map<String, Any>>?.canaryContext(): Map<String, Any> {
    if (this.isNullOrEmpty()) {
      return emptyMap()
    }

    val stage = find { it["refId"] == "canary" }
      ?: return emptyMap()

    @Suppress("UNCHECKED_CAST")
    return stage.getOrDefault("context", emptyMap<String, Any>()) as Map<String, Any>
  }
}

fun CanaryConstraint.toStageBase(
  cloudDriverCache: CloudDriverCache,
  metricsAccount: String,
  storageAccount: String,
  app: String,
  control: Map<String, Any?>,
  experiment: Map<String, Any?>
): Map<String, Any> =
  mapOf(
    "type" to "kayentaCanary",
    "user" to "Spinnaker",
    "name" to "Canary Analysis",
    "refId" to "canary",
    "analysisType" to "realTimeAutomatic",
    "canaryConfig" to mapOf(
      "beginCanaryAnalysisAfterMins" to beginAnalysisAfter.toMinutes(),
      "canaryAnalysisIntervalMins" to canaryAnalysisInterval.toMinutes(),
      "canaryConfigId" to canaryConfigId,
      "lifetimeDuration" to lifetime,
      "metricsAccountName" to metricsAccount,
      "scopes" to listOf(
        mapOf(
          "extendedScopeParams" to mapOf(
            "dataset" to "regional",
            "type" to "asg",
            "environment" to cloudDriverCache
              .credentialBy(source.account)
              .attributes
              .getOrDefault("environment", "test")
          )
        )
      ),
      "scoreThresholds" to mapOf(
        "marginal" to marginalScore,
        "pass" to passScore
      ),
      "storageAccountName" to storageAccount
    ),
    "deployments" to mapOf(
      "delayBeforeCleanup" to cleanupDelay,
      "baseline" to mapOf(
        "account" to source.account,
        "cloudProvider" to source.cloudProvider,
        "cluster" to source.cluster,
        "application" to app
      ),
      "serverGroupPairs" to listOf(
        mapOf(
          "control" to control,
          "experiment" to experiment
        )
      )
    )
  )
