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

package com.netflix.spinnaker.clouddriver.titus.deploy.ops;

import java.util.List;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient;
import com.netflix.spinnaker.clouddriver.titus.client.model.JobDisruptionBudgetUpdateRequest;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertJobDisruptionBudgetDescription;

public class UpsertTitusJobDisruptionBudgetAtomicOperation implements AtomicOperation<Void> {

  private static final String PHASE = "UPSERT_TITUS_JOB_DISRUPTION_BUDGET";
  private final TitusClientProvider titusClientProvider;
  private final UpsertJobDisruptionBudgetDescription description;

  public UpsertTitusJobDisruptionBudgetAtomicOperation(TitusClientProvider titusClientProvider, UpsertJobDisruptionBudgetDescription description) {
    this.titusClientProvider = titusClientProvider;
    this.description = description;
  }

  @Override public Void operate(List priorOutputs) {

    TitusClient titusClient = titusClientProvider.getTitusClient(description.getCredentials(), description.getRegion());
    getTask().updateStatus(PHASE, "Updating Titus Job Disruption: " + description.getJobId() + "...");

    titusClient.updateDisruptionBudget(new JobDisruptionBudgetUpdateRequest()
      .withJobId(description.getJobId())
      .withDisruptionBudget(description.getDisruptionBudget())
    );
    return null;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

}
