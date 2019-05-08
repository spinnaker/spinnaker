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

import java.util.*;
import javax.annotation.Nonnull;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertDisruptionBudgetTask extends AbstractCloudProviderAwareTask {

  private final KatoService katoService;

  @Autowired
  public UpsertDisruptionBudgetTask(KatoService katoService) {
    this.katoService = katoService;
  }

  @Override
  public TaskResult execute(@Nonnull Stage stage) {

    Map request = stage.mapTo(Map.class);
    List<Map<String, Map>> operations = new ArrayList<>();

    Map<String, Object> operation = new HashMap<>();
    operation.put("credentials", request.get("credentials"));
    operation.put("region", request.get("region"));
    operation.put("jobId", request.get("jobId"));
    operation.put("disruptionBudget", request.get("disruptionBudget"));
    operations.add(Collections.singletonMap("upsertDisruptionBudget", operation));

    TaskId taskId = katoService.requestOperations(request.get("cloudProvider").toString(), operations)
      .toBlocking().first();

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("notification.type", "upsertDisruptionBudget");
    outputs.put("kato.last.task.id", taskId);
    outputs.put("titus.region", request.get("region"));
    outputs.put("titus.account.name", request.get("credentials"));

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }
}

