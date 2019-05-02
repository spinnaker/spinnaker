/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kayenta.pipeline

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kayenta.CanaryScope
import com.netflix.spinnaker.orca.kayenta.CanaryScopes
import com.netflix.spinnaker.orca.kayenta.model.KayentaCanaryContext
import com.netflix.spinnaker.orca.kayenta.model.RunCanaryContext
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HashMap

@Component
class RunCanaryIntervalsStage(private val clock: Clock) : StageDefinitionBuilder {

  private val mapper = OrcaObjectMapper
    .newInstance()
    .disable(WRITE_DATES_AS_TIMESTAMPS) // we want Instant serialized as ISO string

  override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
  }

  private fun getDeployDetails(stage: Stage) : DeployedServerGroupContext? {
    val deployedServerGroupsStage = stage.parent?.execution?.stages?.find {
      it.type == DeployCanaryServerGroupsStage.STAGE_TYPE && it.parentStageId == stage.parentStageId
    }
    if (deployedServerGroupsStage == null) {
      return null
    }
    val deployedServerGroups = deployedServerGroupsStage.outputs["deployedServerGroups"] as List<*>
    val data = deployedServerGroups.first() as Map<String, String>
    return DeployedServerGroupContext.from(data)
  }

  override fun beforeStages(parent: Stage, graph: StageGraphBuilder) {
    val canaryConfig = parent.mapTo<KayentaCanaryContext>("/canaryConfig")

    val lifetime: Duration = if (canaryConfig.endTime != null) {
      Duration.ofMinutes((canaryConfig.startTime ?: Instant.now(clock))
        .until(canaryConfig.endTime, ChronoUnit.MINUTES))
    } else if (canaryConfig.lifetime != null) {
      canaryConfig.lifetime
    } else {
      throw IllegalArgumentException("Canary stage configuration must include either `endTime` or `lifetimeDuration`.")
    }

    var canaryAnalysisInterval = canaryConfig.canaryAnalysisInterval ?: lifetime
    if (canaryAnalysisInterval == Duration.ZERO || canaryAnalysisInterval > lifetime) {
      canaryAnalysisInterval = lifetime
    }

    if (canaryConfig.beginCanaryAnalysisAfter > Duration.ZERO) {
      graph.append {
        it.type = WaitStage.STAGE_TYPE
        it.name = "Warmup Wait"
        it.context["waitTime"] = canaryConfig.beginCanaryAnalysisAfter.seconds
      }
    }

    val numIntervals = (lifetime.toMinutes() / canaryAnalysisInterval.toMinutes()).toInt()

    for (i in 1..numIntervals) {
      // If an end time was explicitly specified, we don't need to synchronize
      // the execution of the canary pipeline with the real time.
      if (canaryConfig.endTime == null) {
        graph.append {
          it.type = WaitStage.STAGE_TYPE
          it.name = "Interval Wait #$i"
          it.context["waitTime"] = canaryAnalysisInterval.seconds
        }
      }

      val runCanaryContext = RunCanaryContext(
        canaryConfig.metricsAccountName,
        canaryConfig.configurationAccountName,
        canaryConfig.storageAccountName,
        canaryConfig.canaryConfigId,
        buildRequestScopes(canaryConfig, getDeployDetails(parent), i, canaryAnalysisInterval),
        canaryConfig.scoreThresholds
      )

      graph.append {
        it.type = RunCanaryPipelineStage.STAGE_TYPE
        it.name = "${RunCanaryPipelineStage.STAGE_NAME_PREFIX}$i"
        it.context.putAll(mapper.convertValue<Map<String, Any>>(runCanaryContext))
        it.context["continuePipeline"] = parent.context["continuePipeline"]
      }
    }
  }

  private fun buildRequestScopes(
    config: KayentaCanaryContext,
    deploymentDetails: DeployedServerGroupContext?,
    interval: Int,
    intervalDuration: Duration
  ): Map<String, CanaryScopes> {
    val requestScopes = HashMap<String, CanaryScopes>()
    config.scopes.forEach { scope ->
      var start: Instant
      val end: Instant

      val warmup = config.beginCanaryAnalysisAfter
      val offset = intervalDuration.multipliedBy(interval.toLong())

      if (config.endTime == null) {
        start = (config.startTime ?: Instant.now(clock)).plus(warmup)
        end = (config.startTime ?: Instant.now(clock)).plus(warmup + offset)
      } else {
        start = (config.startTime ?: Instant.now(clock))
        end = (config.startTime ?: Instant.now(clock)).plus(offset)
      }

      if (config.lookback > Duration.ZERO) {
        start = end.minus(config.lookback)
      }

      val controlExtendedScopeParams = mutableMapOf<String, String?>()
      controlExtendedScopeParams.putAll(scope.extendedScopeParams)
      var controlLocation = scope.controlLocation
      var controlScope = scope.controlScope
      if (deploymentDetails != null) {
        if (!controlExtendedScopeParams.containsKey("dataset")) {
          controlExtendedScopeParams["dataset"] = "regional"
        }
        controlLocation = deploymentDetails.controlLocation
        controlScope = deploymentDetails.controlScope
        controlExtendedScopeParams["type"] = "asg"
        if (deploymentDetails.controlAccountId != null) {
          controlExtendedScopeParams["accountId"] = deploymentDetails.controlAccountId
        }
      }

      val experimentExtendedScopeParams = mutableMapOf<String, String?>()
      experimentExtendedScopeParams.putAll(scope.extendedScopeParams)
      var experimentLocation = scope.experimentLocation
      var experimentScope = scope.experimentScope
      if (deploymentDetails != null) {
        if (!experimentExtendedScopeParams.containsKey("dataset")) {
          experimentExtendedScopeParams["dataset"] = "regional"
        }
        experimentLocation = deploymentDetails.experimentLocation
        experimentScope = deploymentDetails.experimentScope
        experimentExtendedScopeParams["type"] = "asg"
        if (deploymentDetails.experimentAccountId != null) {
          experimentExtendedScopeParams["accountId"] = deploymentDetails.experimentAccountId
        }
      }

      val controlScopeData = CanaryScope(
        controlScope,
        controlLocation,
        start,
        end,
        config.step.seconds,
        controlExtendedScopeParams
      )
      val experimentScopeData = controlScopeData.copy(
        scope = experimentScope,
        location = experimentLocation,
        extendedScopeParams = experimentExtendedScopeParams
      )

      requestScopes[scope.scopeName] = CanaryScopes(
        controlScope = controlScopeData,
        experimentScope = experimentScopeData
      )
    }
    return requestScopes
  }

  override fun getType() = STAGE_TYPE

  companion object {
    @JvmStatic
    val STAGE_TYPE = "runCanaryIntervals"
  }
}

private val KayentaCanaryContext.endTime: Instant?
  get() = scopes.first().endTime

private val KayentaCanaryContext.startTime: Instant?
  get() = scopes.first().startTime

private val KayentaCanaryContext.step: Duration
  get() = Duration.ofSeconds(scopes.first().step)

data class DeployedServerGroupContext @JsonCreator constructor(
        val controlLocation: String,
        val controlScope: String,
        val controlAccountId: String?,
        val experimentLocation: String,
        val experimentScope: String,
        val experimentAccountId: String?
) {
  companion object {
    fun from(data: Map<String, String>) : DeployedServerGroupContext {
      return DeployedServerGroupContext(
              data["controlLocation"].orEmpty(),
              data["controlScope"].orEmpty(),
              data["controlAccountId"],
              data["experimentLocation"].orEmpty(),
              data["experimentScope"].orEmpty(),
              data["experimentAccountId"]
      )
    }
  }
}
