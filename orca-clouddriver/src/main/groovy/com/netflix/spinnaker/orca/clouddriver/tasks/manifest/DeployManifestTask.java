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
public class DeployManifestTask extends AbstractCloudProviderAwareTask implements Task {
  @Autowired
  KatoService kato;

  @Autowired
  OortService oort;

  @Autowired
  ArtifactResolver artifactResolver;

  @Autowired
  ObjectMapper objectMapper;

  private static final ThreadLocal<Yaml> yamlParser = ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));

  @Autowired
  ContextParameterProcessor contextParameterProcessor;

  RetrySupport retrySupport = new RetrySupport();

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
      if (task.get("manifestArtifactId") == null) {
        throw new IllegalArgumentException("No manifest artifact was specified.");
      }

      if (task.get("manifestArtifactAccount") == null) {
        throw new IllegalArgumentException("No manifest artifact account was specified.");
      }

      Artifact manifestArtifact = artifactResolver.getBoundArtifactForId(stage, task.get("manifestArtifactId").toString());

      if (manifestArtifact == null) {
        throw new IllegalArgumentException("No artifact could be bound to '" + task.get("manifestArtifactId") + "'");
      }

      log.info("Using {} as the manifest to be deployed", manifestArtifact);

      manifestArtifact.setArtifactAccount((String) task.get("manifestArtifactAccount"));
      Object parsedManifests = retrySupport.retry(() -> {
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

          return manifestWrapper.get("manifests");
        } catch (Exception e) {
          log.warn("Failure fetching/parsing manifests from {}", manifestArtifact, e);
          // forces a retry
          throw new IllegalStateException(e);
        }
      }, 10, 200, true); // retry 10x, starting at .2s intervals
      task.put("manifests", parsedManifests);
      task.put("source", "text");
    }

    List<String> requiredArtifactIds = (List<String>) task.get("requiredArtifactIds");
    List<Artifact> requiredArtifacts = new ArrayList<>();
    requiredArtifactIds = requiredArtifactIds == null ? new ArrayList<>() : requiredArtifactIds;
    for (String id : requiredArtifactIds) {
      Artifact requiredArtifact = artifactResolver.getBoundArtifactForId(stage, id);
      if (requiredArtifact == null) {
        throw new IllegalStateException("No artifact with id '" + id + "' could be found in the pipeline context.");
      }

      requiredArtifacts.add(requiredArtifact);
    }

    log.info("Deploying {} artifacts within the provided manifest", requiredArtifacts);

    task.put("requiredArtifacts", requiredArtifacts);
    task.put("optionalArtifacts", artifacts);
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
