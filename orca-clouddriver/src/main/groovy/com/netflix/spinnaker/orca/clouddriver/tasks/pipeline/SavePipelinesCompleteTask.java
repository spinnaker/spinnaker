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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SavePipelinesCompleteTask implements Task {

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override public TaskResult execute(Stage stage) {
    final SavePipelineResultsData savePipelineResults = stage.mapTo(SavePipelineResultsData.class);
    logResults(savePipelineResults.getPipelinesFailedToSave(), "Failed to save pipelines: ");
    logResults(savePipelineResults.getPipelinesCreated(), "Created pipelines: ");
    logResults(savePipelineResults.getPipelinesUpdated(), "Updated pipelines: ");
    if (savePipelineResults.getPipelinesFailedToSave().isEmpty()) {
      return TaskResult.SUCCEEDED;
    }
    return TaskResult.ofStatus(ExecutionStatus.TERMINAL);
  }

  private void logResults(List<PipelineReferenceData> savePipelineSuccesses, String s) {
    if (!savePipelineSuccesses.isEmpty()) {
      log.info(s + savePipelineSuccesses.stream()
        .map(ref -> ref.getApplication() + ":" + ref.getName())
        .collect(Collectors.joining(", ")));
    }
  }
}
