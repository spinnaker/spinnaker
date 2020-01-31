/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.manifest;

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestForceCacheRefreshTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.PatchManifestTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.PromoteManifestKatoOutputsTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ResolvePatchSourceManifestTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ResolveTargetManifestTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.WaitForManifestStableTask;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.artifacts.BindProducedArtifactsTask;
import org.springframework.stereotype.Component;

@Component
public class PatchManifestStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = "patchManifest";

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
        .withTask(ResolveTargetManifestTask.TASK_NAME, ResolveTargetManifestTask.class)
        .withTask(ResolvePatchSourceManifestTask.TASK_NAME, ResolvePatchSourceManifestTask.class)
        .withTask(PatchManifestTask.TASK_NAME, PatchManifestTask.class)
        .withTask("monitorPatch", MonitorKatoTask.class)
        .withTask(PromoteManifestKatoOutputsTask.TASK_NAME, PromoteManifestKatoOutputsTask.class)
        .withTask(ManifestForceCacheRefreshTask.TASK_NAME, ManifestForceCacheRefreshTask.class)
        .withTask(WaitForManifestStableTask.TASK_NAME, WaitForManifestStableTask.class)
        .withTask(BindProducedArtifactsTask.TASK_NAME, BindProducedArtifactsTask.class);
  }
}
