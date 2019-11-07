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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.PatchManifestContext.MergeStrategy;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PatchManifestTask extends AbstractCloudProviderAwareTask implements Task {
  public static final String TASK_NAME = "patchManifest";

  private final ManifestEvaluator manifestEvaluator;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    PatchManifestContext context = stage.mapTo(PatchManifestContext.class);
    MergeStrategy mergeStrategy = context.getOptions().getMergeStrategy();
    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);
    List<Map<Object, Object>> patchBody = result.getManifests();

    if (patchBody == null || patchBody.isEmpty()) {
      throw new IllegalArgumentException(
          "The Patch (Manifest) stage requires a valid patch body. Please add a patch body inline or with an artifact.");
    }
    if (mergeStrategy != MergeStrategy.JSON && patchBody.size() > 1) {
      throw new IllegalArgumentException(
          "Only one patch object is valid when patching with `strategic` and `merge` patch strategies.");
    }

    Map<String, Object> task = new HashMap<>(stage.getContext());
    task.put("source", "text");
    task.put("patchBody", mergeStrategy == MergeStrategy.JSON ? patchBody : patchBody.get(0));
    task.put("requiredArtifacts", result.getRequiredArtifacts());
    task.put("allArtifacts", result.getOptionalArtifacts());

    return manifestEvaluator.buildTaskResult(TASK_NAME, stage, task);
  }
}
