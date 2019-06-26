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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PatchManifestTask extends AbstractCloudProviderAwareTask implements Task {
  public static final String TASK_NAME = "patchManifest";

  private final ManifestEvaluator manifestEvaluator;

  @Value
  @JsonDeserialize(builder = PatchManifestContext.PatchManifestContextBuilder.class)
  @Builder(builderClassName = "PatchManifestContextBuilder", toBuilder = true)
  private static class PatchManifestContext implements ManifestContext {
    private List<Map<Object, Object>> patchBody;
    private Source source;

    private String manifestArtifactId;
    private Artifact manifestArtifact;
    private String manifestArtifactAccount;

    private List<String> requiredArtifactIds;
    private List<BindArtifact> requiredArtifacts;

    @Builder.Default private boolean skipExpressionEvaluation = false;

    @Nullable
    @Override
    public List<Map<Object, Object>> getManifests() {
      return patchBody;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class PatchManifestContextBuilder {}
  }

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
