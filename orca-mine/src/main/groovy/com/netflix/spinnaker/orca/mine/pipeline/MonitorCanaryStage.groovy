/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.mine.pipeline

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.tasks.CleanupCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterCanaryTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class MonitorCanaryStage extends LinearStage implements CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = "monitorCanary"

  @Autowired DeployCanaryStage deployCanaryStage
  @Autowired MineService mineService

  MonitorCanaryStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "registerCanary", RegisterCanaryTask),
      buildStep(stage, "monitorCanary", MonitorCanaryTask),
      buildStep(stage, "cleanupCanary", CleanupCanaryTask),
      buildStep(stage, "monitorCleanup", MonitorKatoTask),
      buildStep(stage, "completeCanary", CompleteCanaryTask)
    ]
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    def cancelCanaryResults = mineService.cancelCanary(stage.context.canary.id as String, "Pipeline execution (${stage.execution?.id}) canceled")
    log.info("Cancelled canary in mine (canaryId: ${stage.context.canary.id}, stageId: ${stage.id}, executionId: ${stage.execution.id})")

    def canary = stage.ancestors { Stage s, StageBuilder stageBuilder -> stageBuilder instanceof CanaryStage }[0]
    def cancelResult = ((CanaryStage) canary.stageBuilder).cancel(canary.stage)
    cancelResult.details.put("canary", cancelCanaryResults)

    return cancelResult
  }
}
