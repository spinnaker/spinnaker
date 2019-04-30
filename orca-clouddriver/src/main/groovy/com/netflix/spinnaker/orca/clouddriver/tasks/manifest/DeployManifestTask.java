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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeployManifestTask extends AbstractCloudProviderAwareTask implements Task {
  private final KatoService kato;
  private final OortService oort;
  private final ArtifactResolver artifactResolver;
  private final ObjectMapper objectMapper;
  private final ContextParameterProcessor contextParameterProcessor;

  private static final ThreadLocal<Yaml> yamlParser = ThreadLocal.withInitial(() -> new Yaml(new SafeConstructor()));
  private final RetrySupport retrySupport = new RetrySupport();

  public static final String TASK_NAME = "deployManifest";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String credentials = getCredentials(stage);
    String cloudProvider = getCloudProvider(stage);

    List<Artifact> artifacts = artifactResolver.getArtifacts(stage);
    DeployManifestContext context = stage.mapTo(DeployManifestContext.class);
    Map<String, Object> task = new HashMap<>(context);
    String artifactSource = context.getSource();
    if (StringUtils.isNotEmpty(artifactSource) && artifactSource.equals("artifact")) {
      Artifact manifestArtifact = artifactResolver.getBoundArtifactForStage(stage, context.getManifestArtifactId(),
        context.getManifestArtifact());

      if (manifestArtifact == null) {
        throw new IllegalArgumentException("No manifest artifact was specified.");
      }

      // Once the legacy artifacts feature is removed, all trigger expected artifacts will be required to define
      // an account up front.
      if(context.getManifestArtifactAccount() != null) {
        manifestArtifact.setArtifactAccount(context.getManifestArtifactAccount());
      }

      if (manifestArtifact.getArtifactAccount() == null) {
        throw new IllegalArgumentException("No manifest artifact account was specified.");
      }

      log.info("Using {} as the manifest to be deployed", manifestArtifact);

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

          Boolean skipExpressionEvaluation = context.getSkipExpressionEvaluation();
          if (skipExpressionEvaluation == null || !skipExpressionEvaluation) {
            manifestWrapper = contextParameterProcessor.process(
              manifestWrapper,
              contextParameterProcessor.buildExecutionContext(stage, true),
              true
            );

            if (manifestWrapper.containsKey("expressionEvaluationSummary")) {
              throw new IllegalStateException("Failure evaluating manifest expressions: " + manifestWrapper.get("expressionEvaluationSummary"));
            }
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

    List<Artifact> requiredArtifacts = new ArrayList<>();
    for (String id : Optional.ofNullable(context.getRequiredArtifactIds()).orElse(emptyList())) {
      Artifact requiredArtifact = artifactResolver.getBoundArtifactForId(stage, id);
      if (requiredArtifact == null) {
        throw new IllegalStateException("No artifact with id '" + id + "' could be found in the pipeline context.");
      }

      requiredArtifacts.add(requiredArtifact);
    }

    // resolve SpEL expressions in artifacts defined inline in the stage
    for (DeployManifestContext.BindArtifact artifact : Optional.ofNullable(context.getRequiredArtifacts()).orElse(emptyList())) {
      Artifact requiredArtifact = artifactResolver.getBoundArtifactForStage(stage, artifact.getExpectedArtifactId(), artifact.getArtifact());

      if (requiredArtifact == null) {
        throw new IllegalStateException("No artifact with id '" + artifact.getExpectedArtifactId() + "' could be found in the pipeline context.");
      }

      requiredArtifacts.add(requiredArtifact);
    }

    log.info("Deploying {} artifacts within the provided manifest", requiredArtifacts);

    task.put("requiredArtifacts", requiredArtifacts);
    task.put("optionalArtifacts", artifacts);

    if (context.getTrafficManagement() != null && context.getTrafficManagement().isEnabled()) {
      task.put("services", context.getTrafficManagement().getOptions().getServices());
      task.put("enableTraffic", context.getTrafficManagement().getOptions().isEnableTraffic());
      task.put("strategy", context.getTrafficManagement().getOptions().getStrategy().name());
    } else {
      // For backwards compatibility, traffic is always enabled to new server groups when the new traffic management
      // features are not enabled.
      task.put("enableTraffic", true);
    }

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
}
