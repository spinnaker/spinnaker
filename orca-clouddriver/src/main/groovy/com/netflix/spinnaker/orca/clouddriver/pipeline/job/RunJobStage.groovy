/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job

import com.netflix.spinnaker.orca.clouddriver.tasks.job.MonitorJobTask
import com.netflix.spinnaker.orca.clouddriver.tasks.job.RunJobTask
import com.netflix.spinnaker.orca.clouddriver.tasks.job.WaitOnJobCompletion
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@Component
@CompileStatic
class RunJobStage implements StageDefinitionBuilder {
  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("runJob", RunJobTask)
      .withTask("monitorDeploy", MonitorJobTask)

    if (!stage.getContext().getOrDefault("waitForCompletion", "true").toString().equalsIgnoreCase("false")) {
      builder.withTask("waitOnJobCompletion", WaitOnJobCompletion)
    }

    if (stage.getContext().containsKey("expectedArtifacts")) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }
  }

  @Override
  void prepareStageForRestart(Stage stage) {
    if (stage.context.jobStatus) {
      if (!stage.context.restartDetails) stage.context.restartDetails = [:]
      stage.context.restartDetails["jobStatus"] = stage.context.jobStatus
      stage.context.restartDetails["completionDetails"] = stage.context.completionDetails
      stage.context.restartDetails["propertyFileContents"] = stage.context.propertyFileContents
      stage.context.restartDetails["deploy.jobs"] = stage.context["deploy.jobs"]
    }
    stage.context.remove("jobStatus")
    stage.context.remove("completionDetails")
    stage.context.remove("propertyFileContents")
    stage.context.remove("deploy.jobs")
  }
}
