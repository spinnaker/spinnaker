/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.proxmox;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class StopProxmoxInstancesTask implements CloudProviderAware, Task {
  private static final String CLOUD_OPERATION_TYPE = "stopInstances";

  private final KatoService kato;

  public StopProxmoxInstancesTask(KatoService kato) {
    this.kato = kato;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);
    var taskId =
        kato.requestOperations(
            cloudProvider, List.of(Map.of(CLOUD_OPERATION_TYPE, stage.getContext())));

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(Map.of("notification.type", "stopinstances", "kato.last.task.id", taskId))
        .build();
  }
}
