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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@Slf4j
public class PatchManifestTask extends AbstractCloudProviderAwareTask implements Task {

  public static final String TASK_NAME = "patchManifest";
  private static final ThreadLocal<Yaml> yamlParser = ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));

  @Autowired
  KatoService kato;

  @Autowired
  OortService oort;

  @Autowired
  ArtifactResolver artifactResolver;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  ContextParameterProcessor contextParameterProcessor;

  @Autowired
  RetrySupport retrySupport;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String credentials = getCredentials(stage);
    String cloudProvider = getCloudProvider(stage);

    List<Artifact> artifacts = artifactResolver.getArtifacts(stage);
    Map task = new HashMap(stage.getContext());
    String artifactSource = (String) task.get("source");

    if (StringUtils.isNotEmpty(artifactSource) && artifactSource.equals("artifact")) {
      Object parsedPatchBody = parsedManifestArtifact(stage, task);
      task.put("patchBody", parsedPatchBody);
      task.put("source", "text");
    }

    List<String> requiredArtifactIds = (List<String>) task.get("requiredArtifactIds");
    requiredArtifactIds = requiredArtifactIds == null ? new ArrayList<>() : requiredArtifactIds;
    List<Artifact> requiredArtifacts = requiredArtifactIds.stream()
      .map(id -> artifactResolver.getBoundArtifactForId(stage, id))
      .collect(Collectors.toList());

    log.info("Patching {} artifacts within the provided manifest", requiredArtifacts);

    task.put("requiredArtifacts", requiredArtifacts);
    task.put("allArtifacts", artifacts);
    Map<String, Map> operation = new ImmutableMap.Builder<String, Map>()
      .put(TASK_NAME, task)
      .build();

    TaskId taskId = kato.requestOperations(cloudProvider, Collections.singletonList(operation)).toBlocking().first();

    Map<String, Object> outputs = new ImmutableMap.Builder<String, Object>()
      .put("kato.result.expected", true)
      .put("kato.last.task.id", taskId)
      .put("deploy.account.name", credentials)
      .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  // TODO(dibyom) : Refactor into ManifestArtifact utils class for both Deploy and Patch.
  private Object parsedManifestArtifact(@Nonnull Stage stage, Map task) {
    Artifact manifestArtifact = artifactResolver.getBoundArtifactForId(stage, task.get("manifestArtifactId").toString());

    if (manifestArtifact == null) {
      throw new IllegalArgumentException("No artifact could be bound to '" + task.get("manifestArtifactId") + "'");
    }

    log.info("Using {} as the manifest to be patched", manifestArtifact);

    manifestArtifact.setArtifactAccount((String) task.get("manifestArtifactAccount"));
    return retrySupport.retry(() -> {
      try {
        Response manifestText = oort.fetchArtifact(manifestArtifact);

        Iterable<Object> rawManifests = yamlParser.get().loadAll(manifestText.getBody().in());
        List<Map> manifests = StreamSupport.stream(rawManifests.spliterator(), false)
          .map(m -> {
            try {
              return Collections.singletonList(objectMapper.convertValue(m, Map.class));
            } catch (Exception e) {
              return (List<Map>) objectMapper.convertValue(m, List.class);
            }
          })
          .flatMap(Collection::stream)
          .collect(Collectors.toList());

        Map<String, Object> manifestWrapper = new HashMap<>();
        manifestWrapper.put("manifests", manifests);

        manifestWrapper = contextParameterProcessor.process(
          manifestWrapper,
          contextParameterProcessor.buildExecutionContext(stage, true),
          true
        );

        if (manifestWrapper.containsKey("expressionEvaluationSummary")) {
          throw new IllegalStateException("Failure evaluating manifest expressions: " + manifestWrapper.get("expressionEvaluationSummary"));
        }
        List<Map> manifestsAsList = (List<Map>) manifestWrapper.get("manifests");
        return manifestsAsList.get(0);
      } catch (Exception e) {
        log.warn("Failure fetching/parsing manifests from {}", manifestArtifact, e);
        // forces a retry
        throw new IllegalStateException(e);
      }
    }, 10, 200, true);
  }

}
