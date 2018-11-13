/*
 * Copyright 2018 Joel Wilsson
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import com.netflix.spinnaker.orca.clouddriver.tasks.artifacts.FindArtifactsFromResourceTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.DynamicResolveManifestTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import org.springframework.stereotype.Component;

@Component
public class FindArtifactsFromResourceStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "findArtifactFromResource";

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder.withTask(DynamicResolveManifestTask.TASK_NAME, DynamicResolveManifestTask.class)
      .withTask(FindArtifactsFromResourceTask.TASK_NAME, FindArtifactsFromResourceTask.class)
      .withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
  }
}
