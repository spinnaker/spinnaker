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
 */

package com.netflix.spinnaker.orca.pipeline.util;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

@Component
public class ArtifactUtils {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper objectMapper;
  private final ExecutionRepository executionRepository;
  private final ContextParameterProcessor contextParameterProcessor;

  @Autowired
  public ArtifactUtils(
      ObjectMapper objectMapper,
      ExecutionRepository executionRepository,
      ContextParameterProcessor contextParameterProcessor) {
    this.objectMapper = objectMapper;
    this.executionRepository = executionRepository;
    this.contextParameterProcessor = contextParameterProcessor;
  }

  public @Nonnull List<Artifact> getArtifacts(@Nonnull Stage stage) {
    if (stage.getContext() instanceof StageContext) {
      return Optional.ofNullable((List<?>) ((StageContext) stage.getContext()).getAll("artifacts"))
          .map(
              list ->
                  list.stream()
                      .filter(Objects::nonNull)
                      .flatMap(it -> ((List<?>) it).stream())
                      .map(
                          a ->
                              a instanceof Map
                                  ? objectMapper.convertValue(a, Artifact.class)
                                  : (Artifact) a)
                      .collect(Collectors.toList()))
          .orElse(emptyList());
    } else {
      log.warn(
          "Unable to read artifacts from unknown context type: {} ({})",
          stage.getContext().getClass(),
          stage.getExecution().getId());
      return emptyList();
    }
  }

  public @Nonnull List<Artifact> getAllArtifacts(@Nonnull Execution execution) {
    return getAllArtifacts(execution, true, Optional.empty());
  }

  public @Nonnull List<Artifact> getAllArtifacts(
      @Nonnull Execution execution,
      Boolean includeTrigger,
      Optional<Predicate<Stage>> stageFilter) {
    // Get all artifacts emitted by the execution's stages; we'll sort the stages topologically,
    // then reverse the result so that artifacts from later stages will appear
    // earlier in the results.
    List<Artifact> emittedArtifacts =
        Stage.topologicalSort(execution.getStages())
            .filter(stageFilter.orElse(s -> true))
            .filter(s -> s.getOutputs().containsKey("artifacts"))
            .flatMap(
                s ->
                    ((List<?>) s.getOutputs().get("artifacts"))
                        .stream()
                            .map(
                                a ->
                                    a instanceof Map
                                        ? objectMapper.convertValue(a, Artifact.class)
                                        : (Artifact) a))
            .collect(Collectors.toList());
    Collections.reverse(emittedArtifacts);

    // Get all artifacts in the parent pipeline's trigger; these artifacts go at the end of the
    // list,
    // after any that were emitted by the pipeline
    if (includeTrigger) {
      List<Artifact> triggerArtifacts =
          objectMapper.convertValue(
              execution.getTrigger().getArtifacts(), new TypeReference<List<Artifact>>() {});

      emittedArtifacts.addAll(triggerArtifacts);
    }

    return emittedArtifacts;
  }

  /**
   * Used to fully resolve a bound artifact on a stage that can either select an expected artifact
   * ID for an expected artifact defined in a prior stage or as a trigger constraint OR define an
   * inline expression-evaluable default artifact.
   *
   * @param stage The stage containing context to evaluate expressions on the bound artifact.
   * @param id An expected artifact id. Either id or artifact must be specified.
   * @param artifact An inline default artifact. Either id or artifact must be specified.
   * @return A bound artifact with expressions evaluated.
   */
  public @Nullable Artifact getBoundArtifactForStage(
      Stage stage, @Nullable String id, @Nullable Artifact artifact) {
    Artifact boundArtifact = id != null ? getBoundArtifactForId(stage, id) : artifact;
    Map<String, Object> boundArtifactMap =
        objectMapper.convertValue(boundArtifact, new TypeReference<Map<String, Object>>() {});

    Map<String, Object> evaluatedBoundArtifactMap =
        contextParameterProcessor.process(
            boundArtifactMap, contextParameterProcessor.buildExecutionContext(stage), true);

    return objectMapper.convertValue(evaluatedBoundArtifactMap, Artifact.class);
  }

  public @Nullable Artifact getBoundArtifactForId(@Nonnull Stage stage, @Nullable String id) {
    if (isEmpty(id)) {
      return null;
    }

    List<ExpectedArtifact> expectedArtifacts;
    if (stage.getContext() instanceof StageContext) {
      expectedArtifacts =
          Optional.ofNullable(
                  (List<?>) ((StageContext) stage.getContext()).getAll("resolvedExpectedArtifacts"))
              .map(
                  list ->
                      list.stream()
                          .filter(Objects::nonNull)
                          .flatMap(it -> ((List<?>) it).stream())
                          .map(
                              a ->
                                  a instanceof Map
                                      ? objectMapper.convertValue(a, ExpectedArtifact.class)
                                      : (ExpectedArtifact) a)
                          .collect(Collectors.toList()))
              .orElse(emptyList());
    } else {
      log.warn(
          "Unable to read resolved expected artifacts from unknown context type: {} ({})",
          stage.getContext().getClass(),
          stage.getExecution().getId());
      expectedArtifacts = new ArrayList<>();
    }

    final Optional<ExpectedArtifact> expectedArtifactOptional =
        expectedArtifacts.stream().filter(e -> e.getId().equals(id)).findFirst();

    expectedArtifactOptional.ifPresent(
        expectedArtifact -> {
          final Artifact boundArtifact = expectedArtifact.getBoundArtifact();
          final Artifact matchArtifact = expectedArtifact.getMatchArtifact();
          if (boundArtifact != null
              && matchArtifact != null
              && boundArtifact.getArtifactAccount() == null) {
            boundArtifact.setArtifactAccount(matchArtifact.getArtifactAccount());
          }
        });

    return expectedArtifactOptional.map(ExpectedArtifact::getBoundArtifact).orElse(null);
  }

  public @Nonnull List<Artifact> getArtifactsForPipelineId(
      @Nonnull String pipelineId, @Nonnull ExecutionCriteria criteria) {
    Execution execution = getExecutionForPipelineId(pipelineId, criteria);

    return execution == null ? Collections.emptyList() : getAllArtifacts(execution);
  }

  public @Nonnull List<Artifact> getArtifactsForPipelineIdWithoutStageRef(
      @Nonnull String pipelineId, @Nonnull String stageRef, @Nonnull ExecutionCriteria criteria) {
    Execution execution = getExecutionForPipelineId(pipelineId, criteria);

    if (execution == null) {
      return Collections.emptyList();
    }

    return getAllArtifacts(execution, true, Optional.of(it -> !stageRef.equals(it.getRefId())));
  }

  public void resolveArtifacts(@Nonnull Map pipeline) {
    Map<String, Object> trigger = (Map<String, Object>) pipeline.get("trigger");
    ImmutableList<ExpectedArtifact> expectedArtifacts =
        Optional.ofNullable((List<?>) pipeline.get("expectedArtifacts")).orElse(emptyList())
            .stream()
            .map(it -> objectMapper.convertValue(it, ExpectedArtifact.class))
            .collect(toImmutableList());

    ImmutableSet<Artifact> receivedArtifacts =
        Stream.concat(
                Optional.ofNullable((List<?>) pipeline.get("receivedArtifacts")).orElse(emptyList())
                    .stream(),
                Optional.ofNullable((List<?>) trigger.get("artifacts")).orElse(emptyList())
                    .stream())
            .map(it -> objectMapper.convertValue(it, Artifact.class))
            .collect(toImmutableSet());

    ArtifactResolver.ResolveResult resolveResult =
        ArtifactResolver.getInstance(
                receivedArtifacts,
                () -> getPriorArtifacts(pipeline),
                /* requireUniqueMatches= */ true)
            .resolveExpectedArtifacts(expectedArtifacts);

    ImmutableSet<Artifact> allArtifacts =
        ImmutableSet.<Artifact>builder()
            .addAll(receivedArtifacts)
            .addAll(resolveResult.getResolvedArtifacts())
            .build();

    try {
      trigger.put(
          "artifacts",
          objectMapper.readValue(objectMapper.writeValueAsString(allArtifacts), List.class));
      trigger.put(
          "expectedArtifacts",
          objectMapper.readValue(
              objectMapper.writeValueAsString(resolveResult.getResolvedExpectedArtifacts()),
              List.class));
      trigger.put(
          "resolvedExpectedArtifacts",
          objectMapper.readValue(
              objectMapper.writeValueAsString(resolveResult.getResolvedExpectedArtifacts()),
              List.class)); // Add the actual expectedArtifacts we included in the ids.
    } catch (IOException e) {
      throw new ArtifactResolutionException(
          "Failed to store artifacts in trigger: " + e.getMessage(), e);
    }
  }

  private List<Artifact> getPriorArtifacts(final Map<String, Object> pipeline) {
    // set pageSize to a single record to avoid hydrating all of the stored Executions for
    // the pipeline, since getArtifactsForPipelineId only uses the most recent Execution from the
    // returned Observable<Execution>
    ExecutionCriteria criteria = new ExecutionCriteria();
    criteria.setPageSize(1);
    criteria.setSortType(ExecutionRepository.ExecutionComparator.START_TIME_OR_ID);
    return getArtifactsForPipelineId((String) pipeline.get("id"), criteria);
  }

  private Execution getExecutionForPipelineId(
      @Nonnull String pipelineId, @Nonnull ExecutionCriteria criteria) {
    return executionRepository.retrievePipelinesForPipelineConfigId(pipelineId, criteria)
        .subscribeOn(Schedulers.io()).toSortedList(startTimeOrId).toBlocking().single().stream()
        .findFirst()
        .orElse(null);
  }

  private static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static Func2<Execution, Execution, Integer> startTimeOrId =
      (a, b) -> {
        Long aStartTime = Optional.ofNullable(a.getStartTime()).orElse(0L);
        Long bStartTime = Optional.ofNullable(b.getStartTime()).orElse(0L);

        int startTimeCmp = bStartTime.compareTo(aStartTime);
        return startTimeCmp != 0L ? startTimeCmp : b.getId().compareTo(a.getId());
      };
}
