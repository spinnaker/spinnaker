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
package com.netflix.spinnaker.orca.clouddriver.pipeline.pipeline;

import com.netflix.spinnaker.orca.clouddriver.tasks.pipeline.*;
import com.netflix.spinnaker.orca.front50.tasks.MonitorFront50Task;
import com.netflix.spinnaker.orca.front50.tasks.SavePipelineTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode.Builder;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;

@Component
public class SavePipelinesFromArtifactStage implements StageDefinitionBuilder {

  @Override
  public void taskGraph(Stage stage, Builder builder) {

    builder
      .withTask("getPipelinesFromArtifact", GetPipelinesFromArtifactTask.class)
      .withLoop(subGraph -> {
        subGraph
          .withTask("preparePipelineToSaveTask", PreparePipelineToSaveTask.class)
          .withTask("savePipeline", SavePipelineTask.class)
          .withTask("waitForPipelineSave", MonitorFront50Task.class)
          .withTask("checkPipelineResults", CheckPipelineResultsTask.class)
          .withTask("checkForRemainingPipelines", CheckForRemainingPipelinesTask.class);
      })
      .withTask("savePipelinesCompleteTask", SavePipelinesCompleteTask.class);
  }

}
