/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import static java.util.Collections.emptyList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.client.Response;

@Component
@Slf4j
@RequiredArgsConstructor
public class ManifestEvaluator implements CloudProviderAware {
  private static final ThreadLocal<Yaml> yamlParser =
      ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));

  private final ArtifactResolver artifactResolver;
  private final OortService oort;
  private final ObjectMapper objectMapper;
  private final ContextParameterProcessor contextParameterProcessor;
  private final KatoService kato;

  private final RetrySupport retrySupport = new RetrySupport();

  @RequiredArgsConstructor
  @Getter
  public static class Result {
    private final List<Map<Object, Object>> manifests;
    private final List<Artifact> requiredArtifacts;
    private final List<Artifact> optionalArtifacts;
  }

  public Result evaluate(Stage stage, ManifestContext context) {
    Iterable<Object> rawManifests;
    List<Map<Object, Object>> manifests = Collections.emptyList();
    if (ManifestContext.Source.Artifact.equals(context.getSource())) {
      Artifact manifestArtifact =
          artifactResolver.getBoundArtifactForStage(
              stage, context.getManifestArtifactId(), context.getManifestArtifact());

      if (manifestArtifact == null) {
        throw new IllegalArgumentException("No manifest artifact was specified.");
      }

      // Once the legacy artifacts feature is removed, all trigger expected artifacts will be
      // required to define an account up front.
      if (context.getManifestArtifactAccount() != null) {
        manifestArtifact.setArtifactAccount(context.getManifestArtifactAccount());
      }

      if (manifestArtifact.getArtifactAccount() == null) {
        throw new IllegalArgumentException("No manifest artifact account was specified.");
      }

      log.info("Using {} as the manifest", manifestArtifact);

      rawManifests =
          retrySupport.retry(
              () -> {
                Response manifestText = oort.fetchArtifact(manifestArtifact);
                try (InputStream body = manifestText.getBody().in()) {
                  return yamlParser.get().loadAll(body);
                } catch (Exception e) {
                  log.warn("Failure fetching/parsing manifests from {}", manifestArtifact, e);
                  // forces a retry
                  throw new IllegalStateException(e);
                }
              },
              10,
              200,
              true); // retry 10x, starting at .2s intervals);

      List<Object> unevaluatedManifests =
          StreamSupport.stream(rawManifests.spliterator(), false)
              .map(
                  m -> {
                    try {
                      return Collections.singletonList(objectMapper.convertValue(m, Map.class));
                    } catch (Exception e) {
                      return objectMapper.convertValue(
                          m, new TypeReference<List<Map<Object, Object>>>() {});
                    }
                  })
              .flatMap(Collection::stream)
              .collect(Collectors.toList());

      if (!unevaluatedManifests.isEmpty()) {
        Map<String, Object> manifestWrapper = new HashMap<>();
        manifestWrapper.put("manifests", unevaluatedManifests);

        if (!context.isSkipExpressionEvaluation()) {
          manifestWrapper =
              contextParameterProcessor.process(
                  manifestWrapper,
                  contextParameterProcessor.buildExecutionContext(stage, true),
                  true);

          if (manifestWrapper.containsKey("expressionEvaluationSummary")) {
            throw new IllegalStateException(
                "Failure evaluating manifest expressions: "
                    + manifestWrapper.get("expressionEvaluationSummary"));
          }
        }
        manifests = (List<Map<Object, Object>>) manifestWrapper.get("manifests");
      }
    } else {
      manifests = context.getManifests();
    }

    List<Artifact> requiredArtifacts = new ArrayList<>();
    for (String id : Optional.ofNullable(context.getRequiredArtifactIds()).orElse(emptyList())) {
      Artifact requiredArtifact = artifactResolver.getBoundArtifactForId(stage, id);
      if (requiredArtifact == null) {
        throw new IllegalStateException(
            "No artifact with id '" + id + "' could be found in the pipeline context.");
      }

      requiredArtifacts.add(requiredArtifact);
    }

    // resolve SpEL expressions in artifacts defined inline in the stage
    for (ManifestContext.BindArtifact artifact :
        Optional.ofNullable(context.getRequiredArtifacts()).orElse(emptyList())) {
      Artifact requiredArtifact =
          artifactResolver.getBoundArtifactForStage(
              stage, artifact.getExpectedArtifactId(), artifact.getArtifact());

      if (requiredArtifact == null) {
        throw new IllegalStateException(
            "No artifact with id '"
                + artifact.getExpectedArtifactId()
                + "' could be found in the pipeline context.");
      }

      requiredArtifacts.add(requiredArtifact);
    }

    log.info("Artifacts {} are bound to the manifest", requiredArtifacts);

    return new Result(manifests, requiredArtifacts, artifactResolver.getArtifacts(stage));
  }

  public TaskResult buildTaskResult(String taskName, Stage stage, Map<String, Object> task) {
    Map<String, Map> operation =
        new ImmutableMap.Builder<String, Map>().put(taskName, task).build();

    TaskId taskId =
        kato.requestOperations(getCloudProvider(stage), Collections.singletonList(operation))
            .toBlocking()
            .first();

    Map<String, Object> outputs =
        new ImmutableMap.Builder<String, Object>()
            .put("kato.result.expected", true)
            .put("kato.last.task.id", taskId)
            .put("deploy.account.name", getCredentials(stage))
            .build();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }
}
