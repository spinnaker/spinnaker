/*
 * Copyright 2017 Google, Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public final class DeployManifestTask implements CloudProviderAware, Task {
  public static final String TASK_NAME = "deployManifest";

  private final KatoService katoService;

  @Autowired
  public DeployManifestTask(KatoService katoService) {
    this.katoService = katoService;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    ImmutableMap<String, Map> operation = getOperation(stage);
    TaskId taskId = executeOperation(stage, operation);
    ImmutableMap<String, Object> outputs = getOutputs(stage, taskId);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  /** Build the operation map necessary for a DeployManifestTask. */
  public static ImmutableMap<String, Map> getOperation(StageExecution stage) {
    DeployManifestContext context = stage.mapTo(DeployManifestContext.class);

    Map<String, Object> task = new HashMap<>(stage.getContext());

    task.put("source", "text");
    if (context.getTrafficManagement().isEnabled()) {
      task.put("services", context.getTrafficManagement().getOptions().getServices());
      task.put("enableTraffic", context.getTrafficManagement().getOptions().isEnableTraffic());
      task.put("strategy", context.getTrafficManagement().getOptions().getStrategy().name());
    } else {
      // For backwards compatibility, traffic is always enabled to new server groups when the new
      // traffic management
      // features are not enabled.
      task.put("enableTraffic", true);
    }

    return ImmutableMap.of(TASK_NAME, task);
  }

  private TaskId executeOperation(StageExecution stage, ImmutableMap<String, Map> operation) {
    return katoService.requestOperations(getCloudProvider(stage), ImmutableList.of(operation));
  }

  private ImmutableMap<String, Object> getOutputs(StageExecution stage, TaskId taskId) {
    return new ImmutableMap.Builder<String, Object>()
        .put("kato.result.expected", true)
        .put("kato.last.task.id", taskId)
        .put("deploy.account.name", Objects.requireNonNull(getCredentials(stage)))
        .build();
  }
}
