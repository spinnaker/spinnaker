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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.schedulers.Schedulers;

@Component
@NonnullByDefault
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

  public List<Artifact> getArtifacts(StageExecution stage) {
    if (!(stage.getContext() instanceof StageContext)) {
      log.warn(
          "Unable to read artifacts from unknown context type: {} ({})",
          stage.getContext().getClass(),
          stage.getExecution().getId());
      return emptyList();
    }

    return Optional.ofNullable((List<?>) ((StageContext) stage.getContext()).getAll("artifacts"))
        .map(Collection::stream)
        .orElse(Stream.empty())
        .filter(Objects::nonNull)
        .flatMap(it -> ((List<?>) it).stream())
        .map(a -> a instanceof Map ? objectMapper.convertValue(a, Artifact.class) : (Artifact) a)
        .collect(Collectors.toList());
  }

  public List<Artifact> getAllArtifacts(PipelineExecution execution) {
    return getAllArtifacts(execution, s -> true);
  }

  private List<Artifact> getAllArtifacts(
      PipelineExecution execution, Predicate<StageExecution> stageFilter) {
    // Get all artifacts emitted by the execution's stages; we'll sort the stages topologically,
    // then reverse the result so that artifacts from later stages will appear
    // earlier in the results.
    List<Artifact> emittedArtifacts =
        StageExecutionImpl.topologicalSort(execution.getStages())
            .filter(stageFilter)
            .filter(s -> s.getOutputs().containsKey("artifacts"))
            .flatMap(s -> ((List<?>) s.getOutputs().get("artifacts")).stream())
            .map(
                a -> a instanceof Map ? objectMapper.convertValue(a, Artifact.class) : (Artifact) a)
            .collect(Collectors.toList());
    Collections.reverse(emittedArtifacts);

    // Get all artifacts in the parent pipeline's trigger; these artifacts go at the end of the
    // list, after any that were emitted by the pipeline
    emittedArtifacts.addAll(execution.getTrigger().getArtifacts());

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
      StageExecution stage, @Nullable String id, @Nullable Artifact artifact) {
    Artifact boundArtifact = id != null ? getBoundArtifactForId(stage, id) : artifact;
    Map<String, Object> boundArtifactMap =
        objectMapper.convertValue(boundArtifact, new TypeReference<Map<String, Object>>() {});

    Map<String, Object> evaluatedBoundArtifactMap =
        contextParameterProcessor.process(
            boundArtifactMap, contextParameterProcessor.buildExecutionContext(stage), true);

    return objectMapper.convertValue(evaluatedBoundArtifactMap, Artifact.class);
  }

  public @Nullable Artifact getBoundArtifactForId(StageExecution stage, @Nullable String id) {
    if (StringUtils.isEmpty(id)) {
      return null;
    }

    if (!(stage.getContext() instanceof StageContext)) {
      log.warn(
          "Unable to read resolved expected artifacts from unknown context type: {} ({})",
          stage.getContext().getClass(),
          stage.getExecution().getId());
      return null;
    }

    return Optional.ofNullable(
            (List<?>) ((StageContext) stage.getContext()).getAll("resolvedExpectedArtifacts"))
        .map(Collection::stream)
        .orElse(Stream.empty())
        .filter(Objects::nonNull)
        .flatMap(it -> ((List<?>) it).stream())
        .map(
            a ->
                a instanceof Map
                    ? objectMapper.convertValue(a, ExpectedArtifact.class)
                    : (ExpectedArtifact) a)
        .filter(e -> e.getId().equals(id))
        .findFirst()
        .flatMap(this::getBoundArtifact)
        .orElse(null);
  }

  private Optional<Artifact> getBoundArtifact(@Nonnull ExpectedArtifact expectedArtifact) {
    String matchAccount =
        Optional.ofNullable(expectedArtifact.getMatchArtifact())
            .map(Artifact::getArtifactAccount)
            .orElse(null);
    return Optional.ofNullable(expectedArtifact.getBoundArtifact())
        .map(
            boundArtifact -> {
              if (Strings.isNullOrEmpty(boundArtifact.getArtifactAccount())) {
                return boundArtifact.toBuilder().artifactAccount(matchAccount).build();
              } else {
                return boundArtifact;
              }
            });
  }

  public List<Artifact> getArtifactsForPipelineId(String pipelineId, ExecutionCriteria criteria) {
    return getExecutionForPipelineId(pipelineId, criteria)
        .map(this::getAllArtifacts)
        .orElse(Collections.emptyList());
  }

  public List<Artifact> getArtifactsForPipelineIdWithoutStageRef(
      String pipelineId, String stageRef, ExecutionCriteria criteria) {
    return getExecutionForPipelineId(pipelineId, criteria)
        .map(e -> getAllArtifacts(e, it -> !stageRef.equals(it.getRefId())))
        .orElse(Collections.emptyList());
  }

  public void resolveArtifacts(Map pipeline) {
    Map<String, Object> trigger = (Map<String, Object>) pipeline.get("trigger");
    List<?> expectedArtifactIds =
        (List<?>) trigger.getOrDefault("expectedArtifactIds", emptyList());
    ImmutableList<ExpectedArtifact> expectedArtifacts =
        Optional.ofNullable((List<?>) pipeline.get("expectedArtifacts"))
            .map(Collection::stream)
            .orElse(Stream.empty())
            .map(it -> objectMapper.convertValue(it, ExpectedArtifact.class))
            .filter(
                artifact ->
                    expectedArtifactIds.contains(artifact.getId())
                        || artifact.isUseDefaultArtifact()
                        || artifact.isUsePriorArtifact())
            .collect(toImmutableList());

    ImmutableSet<Artifact> receivedArtifacts =
        Stream.concat(
                Optional.ofNullable((List<?>) pipeline.get("receivedArtifacts"))
                    .map(Collection::stream)
                    .orElse(Stream.empty()),
                Optional.ofNullable((List<?>) trigger.get("artifacts"))
                    .map(Collection::stream)
                    .orElse(Stream.empty()))
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

  /**
   * If the input account is non-null and non-empty, returns a copy of the input artifact with its
   * account set to the input account. Otherwise, returns the input artifact unmodified.
   *
   * <p>This function never mutates the input artifact.
   *
   * <p>A number of stages expect an artifact's account to be defined somewhere outside the artifact
   * (such as a field on the stage context). These stages need to take the resolved artifact and
   * augment it with an account at some point after it has been resolved.
   *
   * <p>This pattern is not encouraged, an in general the account should be specified on the
   * expected artifact in the stage. To simplify the code in these legacy stages, this function will
   * augment the supplied artifact with the supplied account (if non-empty and non-null).
   *
   * @param artifact The artifact to augment with an account
   * @param account The account to add to the artifact
   * @return The augmented artifact
   */
  @CheckReturnValue
  public static Artifact withAccount(@Nonnull Artifact artifact, @Nullable String account) {
    if (Strings.isNullOrEmpty(account)) {
      return artifact;
    }
    return artifact.toBuilder().artifactAccount(account).build();
  }

  private List<Artifact> getPriorArtifacts(Map<String, Object> pipeline) {
    // set pageSize to a single record to avoid hydrating all of the stored Executions for
    // the pipeline, since getArtifactsForPipelineId only uses the most recent Execution from the
    // returned Observable<Execution>
    ExecutionCriteria criteria = new ExecutionCriteria();
    criteria.setPageSize(1);
    criteria.setSortType(ExecutionRepository.ExecutionComparator.START_TIME_OR_ID);
    return getArtifactsForPipelineId((String) pipeline.get("id"), criteria);
  }

  private Optional<PipelineExecution> getExecutionForPipelineId(
      String pipelineId, ExecutionCriteria criteria) {
    return executionRepository
        .retrievePipelinesForPipelineConfigId(pipelineId, criteria)
        .subscribeOn(Schedulers.io())
        .toList()
        .toBlocking()
        .single()
        .stream()
        .min(startTimeOrId);
  }

  private static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  // Consider moving this to ExecutionRepository.ExecutionComparator or re-using START_TIME_OR_ID
  // from there. The only difference is that START_TIME_OR_ID sorts executions with a null start
  // time first, followed by executions in order of recency (by start time then by id). We don't
  // want executions with a null start time showing up first here as then a single execution with
  // a null start time would always get selected by getExecutionForPipelineId.
  private static Comparator<PipelineExecution> startTimeOrId =
      Comparator.comparing(
              PipelineExecution::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(PipelineExecution::getId, Comparator.reverseOrder());
}
