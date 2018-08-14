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

import com.netflix.spinnaker.orca.CancellableStage
import com.netflix.spinnaker.orca.igor.tasks.MonitorJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.MonitorQueuedJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.StartJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.StopJenkinsJobTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
public class JenkinsStage implements StageDefinitionBuilder, CancellableStage {
  @Autowired StopJenkinsJobTask stopJenkinsJobTask

  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("start${getType().capitalize()}Job", startJobTaskClass())
      .withTask("waitFor${getType().capitalize()}JobStart", waitForJobStartTaskClass())

    if (!stage.getContext().getOrDefault("waitForCompletion", "true").toString().equalsIgnoreCase("false")) {
      builder.withTask("monitor${getType().capitalize()}Job", waitForCompletionTaskClass())
    }

    if (stage.context.containsKey("expectedArtifacts")) {
      builder
        .withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class)
    }
  }

  Class startJobTaskClass() {
    return StartJenkinsJobTask.class
  }

  Class waitForJobStartTaskClass() {
    return MonitorQueuedJenkinsJobTask.class
  }

  Class waitForCompletionTaskClass() {
    return MonitorJenkinsJobTask.class
  }

  @Override
  void prepareStageForRestart(Stage stage) {
    if (stage.context.buildInfo) {
      if (!stage.context.restartDetails) stage.context.restartDetails = [:]
      stage.context.restartDetails["previousBuildInfo"] = stage.context.buildInfo
    }
    stage.context.remove("buildInfo")
    stage.context.remove("buildNumber")
  }

  @Override
  CancellableStage.Result cancel(Stage stage) {
    log.info("Cancelling stage (stageId: ${stage.id}, executionId: ${stage.execution.id}, context: ${stage.context as Map})")

    try {
      stopJenkinsJobTask.execute(stage)
    } catch (Exception e) {
      log.error("Failed to cancel stage (stageId: ${stage.id}, executionId: ${stage.execution.id}), e: ${e.message}", e)
    }

    return new CancellableStage.Result(stage, [:])
  }
}
