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

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EvaluateCloudFormationChangeSetExecutionTask
    implements OverridableTimeoutRetryableTask {

  @Override
  public @Nonnull TaskResult execute(Stage stage) {
    String actionOnReplacement =
        (String) Optional.ofNullable(stage.getContext().get("actionOnReplacement")).orElse("ask");

    if (!actionOnReplacement.equals("ask")) {
      return TaskResult.SUCCEEDED;
    }

    Optional<Map> currentChangeSet = getCurrentChangeSet(stage);
    Optional<Boolean> changeSetContainsReplacement = getChangeSetIsReplacement(stage);

    if (!changeSetContainsReplacement.isPresent()) {
      Boolean isReplacement = isAnyChangeSetReplacement(currentChangeSet.get());
      Map<String, Object> context = stage.getContext();
      context.put("changeSetContainsReplacement", isReplacement);
      return TaskResult.builder(ExecutionStatus.RUNNING).context(context).build();
    }

    if (changeSetContainsReplacement.orElse(false)) {
      Optional<String> changeSetExecutionChoice = getChangeSetExecutionChoice(stage);
      if (changeSetExecutionChoice.isPresent()) {
        Map<String, Object> context = stage.getContext();
        context.put("actionOnReplacement", changeSetExecutionChoice);
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
      }
      return TaskResult.RUNNING;
    } else {
      return TaskResult.SUCCEEDED;
    }
  }

  @Override
  public long getBackoffPeriod() {
    return 1_000;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.DAYS.toMillis(3);
  }

  private Optional getChangeSetExecutionChoice(Stage stage) {
    return Optional.ofNullable(stage.getContext().get("changeSetExecutionChoice"));
  }

  private Optional getChangeSetIsReplacement(Stage stage) {
    return Optional.ofNullable(stage.getContext().get("changeSetContainsReplacement"));
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
