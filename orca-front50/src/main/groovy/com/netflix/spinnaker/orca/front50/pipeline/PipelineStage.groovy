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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.RestartableStage
import com.netflix.spinnaker.orca.front50.tasks.MonitorPipelineTask
import com.netflix.spinnaker.orca.front50.tasks.StartPipelineTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import groovy.transform.CompileStatic
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component


@Component
@CompileStatic
class PipelineStage extends LinearStage implements RestartableStage {
  public static final String PIPELINE_CONFIG_TYPE = "pipeline"

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
}
