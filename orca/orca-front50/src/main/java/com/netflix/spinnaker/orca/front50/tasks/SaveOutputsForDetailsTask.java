/*
 * Copyright 2022 Armory, Inc.
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

package com.netflix.spinnaker.orca.front50.tasks;

import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.multiplepipelines.RunMultiplePipelinesOutputs;
import java.util.List;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SaveOutputsForDetailsTask implements Task {

  private final Logger logger = LoggerFactory.getLogger(SaveOutputsForDetailsTask.class);

  @SuppressWarnings("unchecked")
  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.info("started SaveOutputsTask task loop ended");
    stage.getOutputs().clear();
    stage.getContext().remove("orderOfExecutions");
    List<RunMultiplePipelinesOutputs> multiplePipelinesOutputsList =
        (List<RunMultiplePipelinesOutputs>)
            stage.getContext().remove("runMultiplePipelinesOutputs");
    stage.getOutputs().put("executionsList", multiplePipelinesOutputsList);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(stage.getContext())
        .outputs(stage.getOutputs())
        .build();
  }
}
