/*
 * Copyright (c) 2019 Adevinta
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
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeleteCloudFormationChangeSetTask extends AbstractCloudProviderAwareTask
    implements Task {

  @Autowired KatoService katoService;

  public static final String TASK_NAME = "deleteCloudFormationChangeSet";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> stageContext = stage.getContext();

    String stackName = (String) stageContext.get("stackName");
    String changeSetName = (String) stageContext.get("changeSetName");

    if ((boolean) Optional.ofNullable(stageContext.get("deleteChangeSet")).orElse(false)) {
      log.debug(
          "Deleting CloudFormation changeset {} from stack {} as requested.",
          changeSetName,
          stackName);
      List<String> regions = (List<String>) stageContext.get("regions");
      String region =
          regions.stream()
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "No regions selected. At least one region must be chosen."));

      Map<String, Object> task = new HashMap<>();
      task.put("stackName", stackName);
      task.put("changeSetName", changeSetName);
      task.put("region", region);
      task.put("credentials", getCredentials(stage));

      Map<String, Map> operation =
          new ImmutableMap.Builder<String, Map>().put(TASK_NAME, task).build();

      TaskId taskId =
          katoService.requestOperations(cloudProvider, Collections.singletonList(operation));

      Map<String, Object> context =
          new ImmutableMap.Builder<String, Object>()
              .put("kato.result.expected", false)
              .put("kato.last.task.id", taskId)
              .build();

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
    } else {
      log.debug(
          "Not deleting CloudFormation ChangeSet {} from stack {}.", changeSetName, stackName);
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).build();
    }
  }
}
