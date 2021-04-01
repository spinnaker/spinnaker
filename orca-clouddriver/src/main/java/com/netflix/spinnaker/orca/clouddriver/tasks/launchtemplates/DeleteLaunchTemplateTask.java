/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.launchtemplates;

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.pipeline.launchtemplates.DeleteLaunchTemplateStage.DeleteLaunchTemplateRequest;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DeleteLaunchTemplateTask implements CloudProviderAware, RetryableTask {
  private final KatoService katoService;

  @Autowired
  public DeleteLaunchTemplateTask(KatoService katoService) {
    this.katoService = katoService;
  }

  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    final DeleteLaunchTemplateRequest request = stage.mapTo(DeleteLaunchTemplateRequest.class);
    final String region = request.getRegion();
    final String cloudProvider = request.getCloudProvider();
    final String credentials = request.getCredentials();
    final List<Map<String, Map>> operations =
        request.getLaunchTemplateIds().stream()
            .map(
                launchTemplateId -> {
                  Map<String, Object> operation = new HashMap<>();
                  operation.put("credentials", credentials);
                  operation.put("region", region);
                  operation.put("launchTemplateId", launchTemplateId);
                  return Collections.<String, Map>singletonMap("deleteLaunchTemplate", operation);
                })
            .collect(toList());

    final TaskId taskId = katoService.requestOperations(cloudProvider, operations);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "deleteLaunchTemplate");
    outputs.put("kato.last.task.id", taskId);
    outputs.put("delete.region", region);
    outputs.put("delete.account.name", credentials);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(10);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(2);
  }
}
