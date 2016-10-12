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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.RestartableStage
import com.netflix.spinnaker.orca.mine.MineService
import com.netflix.spinnaker.orca.mine.tasks.CompleteCanaryTask
import com.netflix.spinnaker.orca.mine.tasks.MonitorAcaTaskTask
import com.netflix.spinnaker.orca.mine.tasks.RegisterAcaTaskTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowire
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class AcaTaskStage extends LinearStage implements CancellableStage, RestartableStage {
  public static final String PIPELINE_CONFIG_TYPE = "acaTask"

  AcaTaskStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Autowired
  MineService mineService


  @Override
  List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "registerGenericCanary", RegisterAcaTaskTask),
      buildStep(stage, "monitorGenericCanary", MonitorAcaTaskTask),
      buildStep(stage, "completeCanary", CompleteCanaryTask)
    ]
  }

  @Override
  Stage prepareStageForRestart(ExecutionRepository executionRepository, Stage stage) {
    stage = super.prepareStageForRestart(executionRepository, stage)
    stage.startTime = null
    stage.endTime = null

    if (stage.context.canary) {
      def previousCanary = stage.context.canary.clone()
      stage.context.restartDetails << [previousCanary: previousCanary]
      cancelCanary(stage, "Restarting AcaTaskStage for execution (${stage.execution?.id}) ")

      stage.context.canary.remove("id")
      stage.context.canary.remove("launchDate")
      stage.context.canary.remove("endDate")
      stage.context.canary.remove("canaryDeployments")
      stage.context.canary.remove("canaryResult")
      stage.context.canary.remove("status")
      stage.context.canary.remove("health")
    }


    stage.tasks.each { com.netflix.spinnaker.orca.pipeline.model.Task task ->
      task.startTime = null
      task.endTime = null
      task.status = ExecutionStatus.NOT_STARTED
    }

    return stage
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")
    def cancelCanaryResults = cancelCanary(stage, "Pipeline execution (${stage.execution?.id}) canceled");
    return new CancellableStage.Result(stage, ["canary": cancelCanaryResults])
  }

  Map cancelCanary(Stage stage, String reason)  {
    if(stage?.context?.canary?.id) {
      def cancelCanaryResults = mineService.cancelCanary(stage.context.canary.id as String, reason)
      log.info("Canceled canary in mine (canaryId: ${stage.context.canary.id}, stageId: ${stage.id}, executionId: ${stage.execution.id}): ${reason}")
      return cancelCanaryResults
    }
  }

}
