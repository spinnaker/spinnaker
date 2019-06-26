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
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
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
    ManifestEvaluator.Result result = manifestEvaluator.evaluate(stage, context);

    Map<String, Object> task = new HashMap<>(stage.getContext());
    task.put("source", "text");
    task.put("patchBody", result.getManifests().get(0));
    task.put("requiredArtifacts", result.getRequiredArtifacts());
    task.put("allArtifacts", result.getOptionalArtifacts());

    return manifestEvaluator.buildTaskResult(TASK_NAME, stage, task);
  }
}
