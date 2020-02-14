/*
 * Copyright 2019 Adevinta.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.cloudformation;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecuteCloudFormationChangeSetTask extends AbstractCloudProviderAwareTask
    implements Task {

  @Autowired KatoService katoService;

  public static final String TASK_NAME = "executeCloudFormationChangeSet";
  public static final String ACTION_ON_REPLACEMENT_SKIP = "skip";
  public static final String ACTION_ON_REPLACEMENT_FAIL = "fail";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {

    String actionOnReplacement =
        (String)
            Optional.ofNullable(stage.getContext().get("actionOnReplacement"))
                .orElse(ACTION_ON_REPLACEMENT_FAIL);

    boolean isChangeSetDeletion =
        (boolean) Optional.ofNullable(stage.getContext().get("deleteChangeSet")).orElse(false);

    Optional<Map> currentChangeSet = getCurrentChangeSet(stage);
    if (currentChangeSet.isPresent()) {
      if (isAnyChangeSetReplacement(currentChangeSet.get())) {
        switch (actionOnReplacement) {
          case ACTION_ON_REPLACEMENT_SKIP:
            return TaskResult.SUCCEEDED;
          case ACTION_ON_REPLACEMENT_FAIL:
            throw new RuntimeException("ChangeSet has a replacement, failing!");
        }
      } else if (isChangeSetDeletion) {
        return TaskResult.SUCCEEDED;
      }
    } else {
      return TaskResult.SUCCEEDED;
    }

    String cloudProvider = getCloudProvider(stage);

    Map<String, Object> stageContext = stage.getContext();

    String credentials = getCredentials(stage);
    String stackName = (String) stageContext.get("stackName");
    String changeSetName = (String) stageContext.get("changeSetName");
    List<String> regions = (List<String>) stageContext.get("regions");
    String region =
        regions.stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No regions selected. At least one region must be chosen."));

    Map<String, Object> task = new HashMap<>();
    task.put("credentials", credentials);
    task.put("stackName", stackName);
    task.put("changeSetName", changeSetName);
    task.put("region", region);

    Map<String, Map> operation =
        new ImmutableMap.Builder<String, Map>().put(TASK_NAME, task).build();

    TaskId taskId =
        katoService
            .requestOperations(cloudProvider, Collections.singletonList(operation))
            .toBlocking()
            .first();

    Map<String, Object> context =
        new ImmutableMap.Builder<String, Object>()
            .put("kato.result.expected", true)
            .put("kato.last.task.id", taskId)
            .put("isChangeSetExecution", true)
            .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

  private Optional<Map> getCurrentChangeSet(Stage stage) {
    String changeSetName = (String) stage.getContext().get("changeSetName");

    Map outputs = stage.getOutputs();
    List<Map> changeSets = (List<Map>) outputs.get("changeSets");
    return changeSets.stream()
        .filter(changeSet -> changeSet.get("name").equals(changeSetName))
        .findFirst();
  }

  private Boolean isAnyChangeSetReplacement(Map changeSet) {
    if (changeSet.containsKey("changes")) {
      List changes = (ArrayList) changeSet.get("changes");
      return changes.stream().anyMatch(change -> changeHasReplacement((Map) change));
    }
    return false;
  }

  private Boolean changeHasReplacement(Map change) {
    Map resourceChange = (Map) change.get("resourceChange");
    return Boolean.parseBoolean((String) resourceChange.get("replacement"));
  }
}
