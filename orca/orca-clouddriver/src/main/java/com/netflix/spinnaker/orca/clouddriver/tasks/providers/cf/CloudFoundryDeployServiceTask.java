/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestContext;
import com.netflix.spinnaker.orca.clouddriver.tasks.manifest.ManifestEvaluator;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CloudFoundryDeployServiceTask implements CloudProviderAware, Task {
  private final ObjectMapper mapper;
  private final KatoService kato;
  private final ManifestEvaluator manifestEvaluator;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = getCredentials(stage);
    Map<String, Object> context = bindArtifactIfNecessary(stage);
    Map<String, Map> operation =
        new ImmutableMap.Builder<String, Map>().put("deployService", context).build();
    TaskId taskId = kato.requestOperations(cloudProvider, Collections.singletonList(operation));

    Map<String, Object> outputs =
        new ImmutableMap.Builder<String, Object>()
            .put("notification.type", "deployService")
            .put("kato.last.task.id", taskId)
            .put("service.region", stage.getContext().get("region"))
            .put("service.account", account)
            .build();
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  @NotNull
  private Map<String, Object> bindArtifactIfNecessary(@Nonnull StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    ServiceManifest manifest = mapper.convertValue(context.get("manifest"), ServiceManifest.class);
    CloudFoundryManifestContext manifestContext =
        CloudFoundryManifestContext.builder()
            .source(ManifestContext.Source.Artifact)
            .manifestArtifactId(manifest.getArtifactId())
            .manifestArtifact(manifest.getArtifact())
            .manifestArtifactAccount(manifest.getArtifact().getArtifactAccount())
            .skipExpressionEvaluation(
                (Boolean)
                    Optional.ofNullable(context.get("skipExpressionEvaluation")).orElse(false))
            .build();
    ManifestEvaluator.Result manifestResult = manifestEvaluator.evaluate(stage, manifestContext);
    context.put("manifest", manifestResult.getManifests());
    return context;
  }
}
