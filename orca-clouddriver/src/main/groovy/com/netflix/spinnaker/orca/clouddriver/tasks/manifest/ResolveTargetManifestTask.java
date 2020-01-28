/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResolveTargetManifestTask extends AbstractCloudProviderAwareTask implements Task {
  @Autowired OortService oortService;

  @Autowired ObjectMapper objectMapper;

  @Autowired RetrySupport retrySupport;

  public static final String TASK_NAME = "resolveTargetManifest";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String credentials = getCredentials(stage);

    StageData stageData = fromStage(stage);

    if (StringUtils.isEmpty(stageData.criteria)) {
      return TaskResult.SUCCEEDED;
    }

    Manifest target =
        retrySupport.retry(
            () ->
                oortService.getDynamicManifest(
                    credentials,
                    stageData.location,
                    stageData.kind,
                    stageData.app,
                    stageData.cluster,
                    stageData.criteria),
            10,
            200,
            true);

    if (target == null) {
      throw new IllegalArgumentException("No manifest could be found matching " + stageData);
    }

    Map<String, Object> outputs =
        new ImmutableMap.Builder<String, Object>().put("manifestName", target.getName()).build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).outputs(outputs).build();
  }

  private StageData fromStage(Stage stage) {
    try {
      return objectMapper.readValue(
          objectMapper.writeValueAsString(stage.getContext()), StageData.class);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Malformed stage context in " + stage + ": " + e.getMessage(), e);
    }
  }

  @Data
  private static class StageData {
    String location;
    String kind;
    String app;
    String cluster;
    String criteria;
  }
}
