/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.api.operations.OperationsContext;
import com.netflix.spinnaker.orca.api.operations.OperationsInput;
import com.netflix.spinnaker.orca.api.operations.OperationsRunner;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.*;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertDisruptionBudgetTask implements CloudProviderAware, Task {

  private final OperationsRunner operationsRunner;

  @Autowired
  public UpsertDisruptionBudgetTask(OperationsRunner operationsRunner) {
    this.operationsRunner = operationsRunner;
  }

  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {

    Map request = stage.mapTo(Map.class);
    List<Map<String, Map>> operations = new ArrayList<>();

    Map<String, Object> operation = new HashMap<>();
    operation.put("credentials", request.get("credentials"));
    operation.put("region", request.get("region"));
    operation.put("jobId", request.get("jobId"));
    operation.put("disruptionBudget", request.get("disruptionBudget"));
    operations.add(Collections.singletonMap("upsertDisruptionBudget", operation));

    OperationsInput operationsInput =
        OperationsInput.of(request.get("cloudProvider").toString(), operations, stage);
    OperationsContext operationsContext = operationsRunner.run(operationsInput);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "upsertDisruptionBudget");
    outputs.put(operationsContext.contextKey(), operationsContext.contextValue());

    // TODO(rz): Why is titus namespacing these?
    outputs.put("titus.region", request.get("region"));
    outputs.put("titus.account.name", request.get("credentials"));

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }
}
