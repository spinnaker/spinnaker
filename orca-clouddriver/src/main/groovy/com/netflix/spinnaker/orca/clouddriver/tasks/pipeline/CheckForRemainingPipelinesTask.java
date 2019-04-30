/*
 * Copyright 2019 Pivotal, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.pipeline;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

@Component
public class CheckForRemainingPipelinesTask implements Task {

  @Override
  public TaskResult execute(Stage stage) {
    final SavePipelinesData savePipelines = stage.mapTo(SavePipelinesData.class);
    if (savePipelines.getPipelinesToSave() == null || savePipelines.getPipelinesToSave().isEmpty()) {
      return TaskResult.SUCCEEDED;
    }
    return TaskResult.ofStatus(ExecutionStatus.REDIRECT);
  }

}
