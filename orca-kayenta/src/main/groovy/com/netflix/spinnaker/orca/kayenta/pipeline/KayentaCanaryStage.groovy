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

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.netflix.spinnaker.orca.kayenta.tasks.AggregateCanaryResultsTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.WaitStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KayentaCanaryStage implements StageDefinitionBuilder {

  @Autowired
  Clock clock

  @Autowired
  WaitStage waitStage

  @Override
  def void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("aggregateCanaryResults", AggregateCanaryResultsTask)
  }

  @Override
  def List<Stage> aroundStages(Stage stage) {
    Map<String, Object> context = stage.getContext()
    Map<String, Object> canaryConfig = context.canaryConfig
    String metricsAccountName = canaryConfig.metricsAccountName
    String storageAccountName = canaryConfig.storageAccountName
    String canaryConfigId = canaryConfig.canaryConfigId
    List<Map> configScopes = canaryConfig.scopes

    if (!configScopes) {
      throw new IllegalArgumentException("Canary stage configuration must contain at least one scope.")
    }

    Map<String, Map> requestScopes = [:]

    configScopes.each { configScope ->
      // TODO(duftler): Externalize these default values.
      String scopeName = configScope.scopeName ?: "default"
      String controlScope = configScope.controlScope
      String controlRegion = configScope.controlRegion
      String experimentScope = configScope.experimentScope
      String experimentRegion = configScope.experimentRegion
      String startTimeIso = configScope.startTimeIso
      String endTimeIso = configScope.endTimeIso
      String step = configScope.step ?: "60"
      Map<String, String> extendedScopeParams = configScope.extendedScopeParams ?: [:]
      Map requestScope = [
        controlScope: [
          scope: controlScope,
          region: controlRegion,
          start: startTimeIso,
          end: endTimeIso,
          step: step,
          extendedScopeParams: extendedScopeParams
        ],
        experimentScope: [
          scope: experimentScope,
          region: experimentRegion,
          start: startTimeIso,
          end: endTimeIso,
          step: step,
          extendedScopeParams: extendedScopeParams
        ]
      ]

      requestScopes[scopeName] = requestScope
    }

    // Using time boundaries from just the first scope since it doesn't really make sense for each scope to have different boundaries.
    // TODO(duftler): Add validation to log warning when time boundaries differ across scopes.
    Map<String, Object> firstScope = configScopes[0]
    String startTimeIso = firstScope.startTimeIso ?: Instant.now(clock).toString()
    Instant startTimeInstant = Instant.parse(startTimeIso)
    String endTimeIso = firstScope.endTimeIso
    Instant endTimeInstant
    Map<String, String> scoreThresholds = canaryConfig.get("scoreThresholds")
    String lifetimeHours = canaryConfig.lifetimeHours
    long lifetimeMinutes
    long beginCanaryAnalysisAfterMins = (canaryConfig.beginCanaryAnalysisAfterMins ?: "0").toLong()
    long lookbackMins = (canaryConfig.lookbackMins ?: "0").toLong()

    if (endTimeIso) {
      endTimeInstant = Instant.parse(firstScope.endTimeIso)
      lifetimeMinutes = startTimeInstant.until(endTimeInstant, ChronoUnit.MINUTES)
    } else if (lifetimeHours) {
      lifetimeMinutes = Duration.ofHours(lifetimeHours.toLong()).toMinutes()
    } else {
      throw new IllegalArgumentException("Canary stage configuration must include either `endTimeIso` or `lifetimeHours`.")
    }

    long canaryAnalysisIntervalMins = canaryConfig.canaryAnalysisIntervalMins ? canaryConfig.canaryAnalysisIntervalMins.toLong() : lifetimeMinutes

    if (canaryAnalysisIntervalMins == 0 || canaryAnalysisIntervalMins > lifetimeMinutes) {
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
        scopes: deepCopy(requestScopes),
        scoreThresholds: scoreThresholds
      ]

      runCanaryContext.scopes.each { _, contextScope ->
        if (!endTimeIso) {
          contextScope.controlScope.start = startTimeInstant.plus(beginCanaryAnalysisAfterMins, ChronoUnit.MINUTES).toString()
          contextScope.controlScope.end = startTimeInstant.plus(beginCanaryAnalysisAfterMins + i * canaryAnalysisIntervalMins, ChronoUnit.MINUTES).toString()
        } else {
          contextScope.controlScope.start = startTimeInstant.toString()
          contextScope.controlScope.end = startTimeInstant.plus(i * canaryAnalysisIntervalMins, ChronoUnit.MINUTES).toString()
        }

        if (lookbackMins) {
          contextScope.controlScope.start = Instant.parse(contextScope.controlScope.end).minus(lookbackMins, ChronoUnit.MINUTES).toString()
        }

        contextScope.experimentScope.start = contextScope.controlScope.start
        contextScope.experimentScope.end = contextScope.controlScope.end
      }

      stages << newStage(stage.execution, RunCanaryPipelineStage.STAGE_TYPE, "Run Canary #$i", runCanaryContext, stage, SyntheticStageOwner.STAGE_BEFORE)
    }

    return stages
  }

  static Object deepCopy(Object sourceObj) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream()
    ObjectOutputStream oos = new ObjectOutputStream(baos)

    oos.writeObject(sourceObj)
    oos.flush()

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())
    ObjectInputStream ois = new ObjectInputStream(bais)

    return ois.readObject()
  }

  @Override
  String getType() {
    "kayentaCanary"
  }
}
