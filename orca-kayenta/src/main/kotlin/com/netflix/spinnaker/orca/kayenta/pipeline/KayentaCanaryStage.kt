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

import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.orca.ext.mapTo
import com.netflix.spinnaker.orca.ext.withTask
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kayenta.CanaryScope
import com.netflix.spinnaker.orca.kayenta.CanaryScopes
import com.netflix.spinnaker.orca.kayenta.model.KayentaCanaryContext
import com.netflix.spinnaker.orca.kayenta.model.RunCanaryContext
import com.netflix.spinnaker.orca.kayenta.tasks.AggregateCanaryResultsTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Duration.ZERO
import java.time.Instant
import java.time.Instant.now
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.*
import java.util.*
import java.util.concurrent.TimeUnit

@Component
class KayentaCanaryStage(private val clock: Clock) : StageDefinitionBuilder {

  private val mapper = OrcaObjectMapper
    .newInstance()
    .disable(WRITE_DATES_AS_TIMESTAMPS) // we want Instant serialized as ISO string

  override fun taskGraph(stage: Stage, builder: TaskNode.Builder) {
    builder.withTask<AggregateCanaryResultsTask>("aggregateCanaryResults")
  }

  override fun beforeStages(parent: Stage, graph: StageGraphBuilder) {
    if (parent.context["deployments"] != null) {
      graph.add {
        it.type = DeployCanaryClustersStage.STAGE_TYPE
        it.name = "Deploy Canary Clusters"
      }
    }

    val canaryConfig = parent.mapTo<KayentaCanaryContext>("/canaryConfig")

    if (canaryConfig.scopes.isEmpty()) {
      throw IllegalArgumentException("Canary stage configuration must contain at least one scope.")
    }

    val lifetime: Duration = if (canaryConfig.endTime != null) {
      Duration.ofMinutes((canaryConfig.startTime ?: now(clock))
        .until(canaryConfig.endTime, MINUTES))
    } else if (canaryConfig.lifetime != null) {
      canaryConfig.lifetime
    } else {
      throw IllegalArgumentException("Canary stage configuration must include either `endTime` or `lifetimeDuration`.")
    }

    var canaryAnalysisInterval = canaryConfig.canaryAnalysisInterval ?: lifetime
    if (canaryAnalysisInterval == ZERO || canaryAnalysisInterval > lifetime) {
      canaryAnalysisInterval = lifetime
    }

    val numIntervals = (lifetime.toMinutes() / canaryAnalysisInterval.toMinutes()).toInt()

    if (canaryConfig.beginCanaryAnalysisAfter > ZERO) {
      graph.append {
        it.type = WaitStage.STAGE_TYPE
        it.name = "Warmup Wait"
        it.context["waitTime"] = canaryConfig.beginCanaryAnalysisAfter.seconds
      }
    }

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
        canaryConfig.storageAccountName,
        canaryConfig.canaryConfigId,
        buildRequestScopes(canaryConfig, i, canaryAnalysisInterval),
        canaryConfig.scoreThresholds
      )

      graph.append {
        it.type = RunCanaryPipelineStage.STAGE_TYPE
        it.name = "Run Canary #$i"
        it.context.putAll(mapper.convertValue<Map<String, Any>>(runCanaryContext))
        it.context["continuePipeline"] = parent.context["continuePipeline"]
      }
    }
  }

  override fun afterStages(parent: Stage, graph: StageGraphBuilder) {
    if (parent.context["deployments"] != null) {
      graph.add {
        it.type = CleanupCanaryClustersStage.STAGE_TYPE
        it.name = "Cleanup Canary Clusters"
      }
    }
  }

  override fun onFailureStages(stage: Stage, graph: StageGraphBuilder) {
    afterStages(stage, graph)
  }

  private fun buildRequestScopes(
    config: KayentaCanaryContext,
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
        start = (config.startTime ?: now(clock)).plus(warmup)
        end = (config.startTime ?: now(clock)).plus(warmup + offset)
      } else {
        start = (config.startTime ?: now(clock))
        end = (config.startTime ?: now(clock)).plus(offset)
      }

      if (config.lookback > ZERO) {
        start = end.minus(config.lookback)
      }

      val controlScope = CanaryScope(
        scope.controlScope,
        scope.controlLocation,
        start,
        end,
        config.step.seconds,
        scope.extendedScopeParams
      )
      val experimentScope = controlScope.copy(
        scope = scope.experimentScope,
        location = scope.experimentLocation
      )

      requestScopes[scope.scopeName] = CanaryScopes(
        controlScope = controlScope,
        experimentScope = experimentScope
      )
    }
    return requestScopes
  }

  override fun getType() = STAGE_TYPE

  companion object {
    @JvmStatic
    val STAGE_TYPE = "kayentaCanary"
  }
}

private val KayentaCanaryContext.endTime: Instant?
  get() = scopes.first().endTime

private val KayentaCanaryContext.startTime: Instant?
  get() = scopes.first().startTime

private val KayentaCanaryContext.step: Duration
  get() = Duration.ofSeconds(scopes.first().step)
