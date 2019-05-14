/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.listeners;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.List;

public class ExecutionCleanupListener implements ExecutionListener {
  @Override
  public void beforeExecution(Persister persister, Execution execution) {
    // do nothing
  }

  @Override
  public void afterExecution(
      Persister persister,
      Execution execution,
      ExecutionStatus executionStatus,
      boolean wasSuccessful) {
    if (!execution.getStatus().isSuccessful()) {
      // only want to cleanup executions that successfully completed
      return;
    }

    List<Stage> stages = execution.getStages();
    stages.forEach(
        it -> {
          if (it.getContext().containsKey("targetReferences")) {
            // remove `targetReferences` as it's large and unnecessary after a pipeline has
            // completed
            it.getContext().remove("targetReferences");
            persister.save(it);
          }
        });
  }
}
