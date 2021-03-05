/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.pipeline;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageGraphBuilder;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.tasks.CreateWebhookTask;
import com.netflix.spinnaker.orca.webhook.tasks.MonitorWebhookTask;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebhookStage implements StageDefinitionBuilder {

  private final MonitorWebhookTask monitorWebhookTask;

  @Autowired
  public WebhookStage(MonitorWebhookTask monitorWebhookTask) {
    this.monitorWebhookTask = monitorWebhookTask;
  }

  @Override
  public void taskGraph(@Nonnull StageExecution stage, @Nonnull TaskNode.Builder builder) {
    StageData stageData = stage.mapTo(StageData.class);

    if (stageData.monitorOnly && !stageData.waitForCompletion) {
      throw new UserException(
          "Can't specify monitorOnly = true and waitForCompletion = false at the same time");
    }

    if (!stageData.monitorOnly) {
      builder.withTask("createWebhook", CreateWebhookTask.class);
    }
    if (stageData.waitForCompletion) {
      if (stageData.waitBeforeMonitor > 0) {
        stage.getContext().putIfAbsent("waitTime", stageData.waitBeforeMonitor);
        builder.withTask("waitBeforeMonitorWebhook", WaitTask.class);
      }

      builder.withTask("monitorWebhook", MonitorWebhookTask.class);
    }
    if (stage.getContext().containsKey("expectedArtifacts")) {
      builder.withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
    }
  }

  @Override
  public void onFailureStages(@Nonnull StageExecution stage, @Nonnull StageGraphBuilder graph) {
    monitorWebhookTask.onCancel(stage);
  }

  @Data
  public static class StageData {
    // Inputs for webhook
    public String url;
    public Object payload;
    public Map<String, Object> customHeaders;
    public List<Integer> failFastStatusCodes;
    public Boolean waitForCompletion;
    public WebhookProperties.StatusUrlResolution statusUrlResolution;
    public String statusUrlJsonPath;
    public boolean monitorOnly;

    @JsonFormat(with = {JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES})
    public HttpMethod method = HttpMethod.POST;

    // Inputs for monitor
    public String statusEndpoint;
    public String statusJsonPath;
    public String progressJsonPath;
    public String cancelEndpoint;

    @JsonFormat(with = {JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES})
    public HttpMethod cancelMethod = HttpMethod.POST;

    public Object cancelPayload;
    public String successStatuses;
    public String canceledStatuses;
    public String terminalStatuses;
    public List<Integer> retryStatusCodes;

    public int waitBeforeMonitor;

    // Outputs
    WebhookResponseStageData webhook;
  }

  @Data
  public static class WebhookResponseStageData {
    public String statusCode;
    public WebhookMonitorResponseStageData monitor;
    public String error;

    // NOTE: The fields below exist in the context because they are inserted by the
    // CreateWebhookTask but they aren't
    //       consumed by Spinnaker so they are commented out below - they are here for informational
    // purposes only
    // String statusEndpoint
    // Integer statusCodeValue
    // Map body
  }

  @Data
  public static class WebhookMonitorResponseStageData {
    public String error;

    // NOTE: The fields below exist in the context because they are inserted by the
    // MonitorWebhookTask but they aren't
    //       consumed by Spinnaker so they are commented out below - they are here for informational
    // purposes only
    // Integer statusCodeValue
    // String statusCode
    // Map body
    // String progressMessage
    // Number percentComplete
  }
}
