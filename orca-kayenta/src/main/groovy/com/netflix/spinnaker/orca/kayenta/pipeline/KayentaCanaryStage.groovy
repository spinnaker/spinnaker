/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.orca.kayenta.tasks.AggregateCanaryResultsTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class KayentaCanaryStage implements StageDefinitionBuilder {

  @Autowired
  Clock clock

  @Autowired
  WaitStage waitStage

  @Override
  def <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder.withTask("aggregateCanaryResults", AggregateCanaryResultsTask)
  }

  @Override
  def <T extends Execution<T>> List<Stage<T>> aroundStages(Stage<T> stage) {
    Map<String, Object> context = stage.getContext()
    Map<String, Object> canaryConfig = context.canaryConfig
    String metricsAccountName = canaryConfig.metricsAccountName
    String storageAccountName = canaryConfig.storageAccountName
    String canaryConfigId = canaryConfig.canaryConfigId
    String controlScope = canaryConfig.controlScope
    String experimentScope = canaryConfig.experimentScope
    String startTimeIso = canaryConfig.startTimeIso ?: Instant.now(clock).toString()
    Instant startTimeInstant = Instant.parse(startTimeIso)
    String endTimeIso = canaryConfig.endTimeIso
    Instant endTimeInstant
    String step = canaryConfig.step ?: "60"
    Map<String, String> extendedScopeParams = canaryConfig.get("extendedScopeParams") ?: [:]
    Map<String, String> scoreThresholds = canaryConfig.get("scoreThresholds")
    String lifetimeHours = canaryConfig.lifetimeHours
    long lifetimeMinutes
    long beginCanaryAnalysisAfterMins = (canaryConfig.beginCanaryAnalysisAfterMins ?: "0").toLong()
    long lookbackMins = (canaryConfig.lookbackMins ?: "0").toLong()

    if (endTimeIso) {
      endTimeInstant = Instant.parse(canaryConfig.endTimeIso)
      lifetimeMinutes = startTimeInstant.until(endTimeInstant, ChronoUnit.MINUTES)
    } else if (lifetimeHours) {
      lifetimeMinutes = Duration.ofHours(lifetimeHours.toLong()).toMinutes()
    } else {
      throw new IllegalArgumentException("Canary stage configuration must include either `endTimeIso` or `lifetimeHours`.")
    }

    long canaryAnalysisIntervalMins = canaryConfig.canaryAnalysisIntervalMins ? canaryConfig.canaryAnalysisIntervalMins.toLong() : lifetimeMinutes

    if (canaryAnalysisIntervalMins == 0) {
      canaryAnalysisIntervalMins = lifetimeMinutes
    }

    long numIntervals = lifetimeMinutes / canaryAnalysisIntervalMins
    List<Stage> stages = []

    if (beginCanaryAnalysisAfterMins) {
      Map warmupWaitContext = [
        waitTime: Duration.ofMinutes(beginCanaryAnalysisAfterMins).getSeconds()
      ]

      stages << newStage(stage.execution, waitStage.type, "Warmup Wait", warmupWaitContext, stage, SyntheticStageOwner.STAGE_BEFORE)
    }

    for (int i = 1; i <= numIntervals; i++) {
      // If an end time was explicitly specified, we don't need to synchronize the execution of the canary pipeline with the real time.
      if (!endTimeIso) {
        Map intervalWaitContext = [
          waitTime: Duration.ofMinutes(canaryAnalysisIntervalMins).getSeconds()
        ]

        stages << newStage(stage.execution, waitStage.type, "Interval Wait #$i", intervalWaitContext, stage, SyntheticStageOwner.STAGE_BEFORE)
      }

      Map runCanaryContext = [
        metricsAccountName: metricsAccountName,
        storageAccountName: storageAccountName,
        canaryConfigId: canaryConfigId,
        controlScope: controlScope,
        experimentScope: experimentScope,
        step: step,
        extendedScopeParams: extendedScopeParams,
        scoreThresholds: scoreThresholds
      ]

      if (!endTimeIso) {
        runCanaryContext.startTimeIso = startTimeInstant.plus(beginCanaryAnalysisAfterMins, ChronoUnit.MINUTES).toString()
        runCanaryContext.endTimeIso = startTimeInstant.plus(beginCanaryAnalysisAfterMins + i * canaryAnalysisIntervalMins, ChronoUnit.MINUTES).toString()
      } else {
        runCanaryContext.startTimeIso = startTimeInstant.toString()
        runCanaryContext.endTimeIso = startTimeInstant.plus(i * canaryAnalysisIntervalMins, ChronoUnit.MINUTES).toString()
      }

      if (lookbackMins) {
        runCanaryContext.startTimeIso = Instant.parse(runCanaryContext.endTimeIso).minus(lookbackMins, ChronoUnit.MINUTES).toString()
      }

      stages << newStage(stage.execution, RunCanaryPipelineStage.STAGE_TYPE, "Run Canary #$i", runCanaryContext, stage, SyntheticStageOwner.STAGE_BEFORE)
    }

    return stages
  }

  @Override
  String getType() {
    "kayentaCanary"
  }
}
