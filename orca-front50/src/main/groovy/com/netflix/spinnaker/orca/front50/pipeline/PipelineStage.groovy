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

package com.netflix.spinnaker.orca.front50.pipeline

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.RestartableStage
import com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask
import com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


@Slf4j
@Component
@CompileStatic
class PipelineStage extends LinearStage implements RestartableStage, CancellableStage {
  public static final String PIPELINE_CONFIG_TYPE = "pipeline"

  @Autowired
  ExecutionRepository executionRepository

  PipelineStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  public List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "startPipeline", StartPipelineTask),
      buildStep(stage, "monitorPipeline", MonitorPipelineTask)
    ]
  }

  @Override
  Stage prepareStageForRestart(Stage stage) {
    stage = super.prepareStageForRestart(stage)
    stage.startTime = null
    stage.endTime = null

    stage.context.remove("status")
    stage.context.remove("executionName")
    stage.context.remove("executionId")

    stage.tasks.each { Task task ->
      task.startTime = null
      task.endTime = null
      task.status = ExecutionStatus.NOT_STARTED
    }

    return stage
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    try {
      if (stage.context.executionId) {
        // flag the child pipeline as canceled (actual cancellation will happen asynchronously)
        executionRepository.cancel(stage.context.executionId as String, "parent pipeline")
      }
    } catch (Exception e) {
      log.info("Failed to cancel stage (stageId: ${stage.id}, executionId: ${stage.execution.id}), e: ${e.message}")
    }

    return new CancellableStage.Result(stage, [:])
  }
}
