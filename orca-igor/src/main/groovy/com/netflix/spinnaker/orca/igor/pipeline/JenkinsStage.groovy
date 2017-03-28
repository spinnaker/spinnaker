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

package com.netflix.spinnaker.orca.igor.pipeline

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.RestartableStage
import com.netflix.spinnaker.orca.igor.tasks.MonitorJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.MonitorQueuedJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.StartJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.StopJenkinsJobTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
@CompileStatic
class JenkinsStage implements StageDefinitionBuilder, RestartableStage, CancellableStage {
  @Autowired StopJenkinsJobTask stopJenkinsJobTask

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    builder
      .withTask("start${getType().capitalize()}Job", StartJenkinsJobTask.class)
      .withTask("waitFor${getType().capitalize()}JobStart", MonitorQueuedJenkinsJobTask.class)

    if (!stage.getContext().getOrDefault("waitForCompletion", "true").toString().equalsIgnoreCase("false")) {
      builder.withTask("monitor${getType().capitalize()}Job", MonitorJenkinsJobTask.class)
    }
  }

  @Override
  Stage prepareStageForRestart(ExecutionRepository executionRepository, Stage stage, Collection<StageDefinitionBuilder> allStageBuilders) {
    stage = StageDefinitionBuilder.StageDefinitionBuilderSupport
      .prepareStageForRestart(executionRepository, stage, this, allStageBuilders)
    stage.startTime = null
    stage.endTime = null

    if (stage.context.buildInfo) {
      stage.context.restartDetails["previousBuildInfo"] = stage.context.buildInfo
    }
    stage.context.remove("buildInfo")
    stage.context.remove("buildNumber")

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
      stopJenkinsJobTask.execute(stage)
    } catch (Exception e) {
      log.info("Failed to cancel stage (stageId: ${stage.id}, executionId: ${stage.execution.id}), e: ${e.message}")
    }

    return new CancellableStage.Result(stage, [:])
  }
}
