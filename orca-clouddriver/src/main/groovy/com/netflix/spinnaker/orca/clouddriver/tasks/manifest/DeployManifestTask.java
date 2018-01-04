/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DeployManifestTask extends AbstractCloudProviderAwareTask implements Task {
  @Autowired
  KatoService kato;

  @Autowired
  ArtifactResolver artifactResolver;

  public static final String TASK_NAME = "deployManifest";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String credentials = getCredentials(stage);
    String cloudProvider = getCloudProvider(stage);

    List<Artifact> artifacts = artifactResolver.getArtifacts(stage);
    Map task = new HashMap(stage.getContext());
    String artifactSource = (String) task.get("source");
    if (StringUtils.isNotEmpty(artifactSource) && artifactSource.equals("artifact")) {
      Artifact manifestArtifact = artifactResolver.getBoundArtifactForId(stage, task.get("manifestArtifactId").toString());
      task.put("manifestArtifact", manifestArtifact);
      log.info("Using {} as the manifest to be deployed", manifestArtifact);
    }

    List<String> deployArtifactIds = (List<String>) task.get("deployArtifactIds");
    deployArtifactIds = deployArtifactIds == null ? new ArrayList<>() : deployArtifactIds;
    List<Artifact> deployArtifacts = deployArtifactIds.stream()
        .map(id -> artifactResolver.getBoundArtifactForId(stage, id))
        .collect(Collectors.toList());

    log.info("Deploying {} artifacts within the provided manifest", deployArtifacts);

    task.put("artifacts", deployArtifacts);
    Map<String, Map> operation = new ImmutableMap.Builder<String, Map>()
        .put(TASK_NAME, task)
        .build();

    TaskId taskId = kato.requestOperations(cloudProvider, Collections.singletonList(operation)).toBlocking().first();

    Map<String, Object> outputs = new ImmutableMap.Builder<String, Object>()
        .put("kato.result.expected", true)
        .put("kato.last.task.id", taskId)
        .put("deploy.account.name", credentials)
        .build();

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
