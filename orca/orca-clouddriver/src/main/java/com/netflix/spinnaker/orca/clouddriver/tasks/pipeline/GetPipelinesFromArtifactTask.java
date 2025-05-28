/*
 * Copyright 2019 Pivotal, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.tasks.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("front50.enabled")
public class GetPipelinesFromArtifactTask implements Task {

  private Logger log = LoggerFactory.getLogger(getClass());
  RetrySupport retrySupport = new RetrySupport();

  private final Front50Service front50Service;
  private final OortService oort;
  private final ObjectMapper objectMapper;
  private final ArtifactUtils artifactUtils;

  public GetPipelinesFromArtifactTask(
      Front50Service front50Service,
      OortService oort,
      ObjectMapper objectMapper,
      ArtifactUtils artifactUtils) {
    this.front50Service = front50Service;
    this.oort = oort;
    this.objectMapper = objectMapper;
    this.artifactUtils = artifactUtils;
  }

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PipelinesArtifactData {
    @JsonProperty("pipelinesArtifactId")
    private String id;

    @JsonProperty("pipelinesArtifact")
    private Artifact inline;
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    final PipelinesArtifactData pipelinesArtifact = stage.mapTo(PipelinesArtifactData.class);
    Artifact resolvedArtifact =
        artifactUtils.getBoundArtifactForStage(
            stage, pipelinesArtifact.getId(), pipelinesArtifact.getInline());
    if (resolvedArtifact == null) {
      throw new IllegalArgumentException(
          "No artifact could be bound to '" + pipelinesArtifact.getId() + "'");
    }
    log.info("Using {} as the pipelines to be saved", pipelinesArtifact);

    String pipelinesText = getPipelinesArtifactContent(resolvedArtifact);

    Map<String, List<Map>> pipelinesFromArtifact = null;
    try {
      pipelinesFromArtifact =
          objectMapper.readValue(pipelinesText, new TypeReference<Map<String, List<Map>>>() {});
    } catch (IOException e) {
      log.warn("Failure parsing pipelines from {}", pipelinesArtifact, e);
      throw new IllegalStateException(e); // forces a retry
    }
    final Map<String, List<Map>> finalPipelinesFromArtifact = pipelinesFromArtifact;
    final Set<String> appNames = pipelinesFromArtifact.keySet();
    final List newAndUpdatedPipelines =
        appNames.stream()
            .flatMap(
                appName -> {
                  final List<Map<String, Object>> existingAppPipelines =
                      Retrofit2SyncCall.execute(front50Service.getPipelines(appName));
                  final List<Map> specifiedAppPipelines = finalPipelinesFromArtifact.get(appName);
                  return specifiedAppPipelines.stream()
                      .map(
                          p -> {
                            final Map<String, Object> pipeline = p;
                            pipeline.put("application", appName);
                            final Optional<Map<String, Object>> matchedExistingPipeline =
                                existingAppPipelines.stream()
                                    .filter(
                                        existingPipeline ->
                                            existingPipeline
                                                .get("name")
                                                .equals(pipeline.get("name")))
                                    .findFirst();
                            matchedExistingPipeline.ifPresent(
                                matchedPipeline -> {
                                  pipeline.put("id", matchedPipeline.get("id"));
                                });
                            return pipeline;
                          })
                      .filter(pipeline -> !pipeline.isEmpty());
                })
            .collect(Collectors.toList());
    final SavePipelinesData output = new SavePipelinesData(null, newAndUpdatedPipelines);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(objectMapper.convertValue(output, new TypeReference<Map<String, Object>>() {}))
        .build();
  }

  private String getPipelinesArtifactContent(Artifact artifact) {
    return retrySupport.retry(
        () -> {
          try (ResponseBody responseBody =
              Retrofit2SyncCall.execute(oort.fetchArtifact(artifact))) {
            return responseBody.string();
          } catch (IOException e) {
            log.warn("Failure reading pipelines from {}", artifact, e);
            throw new IllegalStateException(e); // forces a retry
          }
        },
        10,
        200,
        true);
  }
}
