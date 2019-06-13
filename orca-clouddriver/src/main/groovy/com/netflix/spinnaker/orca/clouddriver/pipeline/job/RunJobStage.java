/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.CancellableStage;
import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.ConsumeArtifactTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.DestroyJobTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.MonitorJobTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.RunJobTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.job.WaitOnJobCompletion;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RunJobStage implements StageDefinitionBuilder, CancellableStage {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final DestroyJobTask destroyJobTask;
  private final ObjectMapper objectMapper = OrcaObjectMapper.newInstance();

  @Autowired
  public RunJobStage(DestroyJobTask destroyJobTask) {
    this.destroyJobTask = destroyJobTask;
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask("runJob", RunJobTask.class).withTask("monitorDeploy", MonitorJobTask.class);

    if (!stage
        .getContext()
        .getOrDefault("waitForCompletion", "true")
        .toString()
        .equalsIgnoreCase("false")) {
      builder.withTask("waitOnJobCompletion", WaitOnJobCompletion.class);
    }

    if (stage.getContext().containsKey("expectedArtifacts")) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }

    if (stage
        .getContext()
        .getOrDefault("consumeArtifactSource", "")
        .toString()
        .equalsIgnoreCase("artifact")) {
      builder.withTask(ConsumeArtifactTask.TASK_NAME, ConsumeArtifactTask.class);
    }
  }

  @Override
  public Result cancel(Stage stage) {
    log.info(
        "Canceling run job stage {} for executionId {}",
        stage.getId(),
        stage.getExecution().getId());
    Map<String, Object> destroyContext = new HashMap<>();

    try {
      RunJobStageContext context =
          objectMapper.convertValue(stage.getContext(), RunJobStageContext.class);
      String jobId = context.getJobStatus().getId();

      if (context.getCloudProvider().equalsIgnoreCase("titus")) {
        destroyContext.put("jobId", jobId);
      } else {
        destroyContext.put("jobName", context.getJobStatus().getName());
      }

      destroyContext.put("cloudProvider", context.getCloudProvider());
      destroyContext.put("region", context.getJobStatus().getRegion());
      destroyContext.put("credentials", context.getCredentials());

      stage.setContext(destroyContext);

      destroyJobTask.execute(stage);
    } catch (Exception e) {
      log.error(
          String.format(
              "Failed to cancel run job (stageId: %s, executionId: %s), e: %s",
              stage.getId(), stage.getExecution().getId(), e.getMessage()),
          e);
    }

    return new Result(stage, destroyContext);
  }

  @Override
  public void prepareStageForRestart(Stage stage) {
    Map<String, Object> context = stage.getContext();

    // preserve previous job details
    if (context.containsKey("jobStatus")) {
      Map<String, Object> restartDetails =
          context.containsKey("restartDetails")
              ? (Map<String, Object>) context.get("restartDetails")
              : new HashMap<>();
      restartDetails.put("jobStatus", context.get("jobStatus"));
      restartDetails.put("completionDetails", context.get("completionDetails"));
      restartDetails.put("propertyFileContents", context.get("propertyFileContents"));
      restartDetails.put("deploy.jobs", context.get("deploy.jobs"));
      context.put("restartDetails", restartDetails);
    }

    context.remove("jobStatus");
    context.remove("completionDetails");
    context.remove("propertyFileContents");
    context.remove("deploy.jobs");
  }
}
