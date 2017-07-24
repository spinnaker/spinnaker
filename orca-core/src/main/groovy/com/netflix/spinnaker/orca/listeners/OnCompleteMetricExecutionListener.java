/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.listeners;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Orchestration;

import java.util.concurrent.TimeUnit;

public class OnCompleteMetricExecutionListener implements ExecutionListener {
  private final Registry registry;

  public OnCompleteMetricExecutionListener(Registry registry) {
    this.registry = registry;
  }

  @Override
  public void afterExecution(Persister persister,
                             Execution execution,
                             ExecutionStatus executionStatus,
                             boolean wasSuccessful) {
    if (!(execution instanceof Orchestration)) {
      // not concerned with pipelines right now (pipelines can have wait stages / manual judgmenets which skew execution time)
      return;
    }


    if (execution.getApplication() == null || execution.getStartTime() == null || execution.getEndTime() == null) {
      // should normally have all attributes but a guard just in case
      return;
    }

    Id id = registry
      .createId("executions.totalTime")
      .withTag("executionType", "orchestration")
      .withTag("successful", Boolean.valueOf(wasSuccessful).toString())
      .withTag("application", execution.getApplication().toLowerCase());

    registry.timer(id).record(execution.getEndTime() - execution.getStartTime(), TimeUnit.MILLISECONDS);
  }
}
