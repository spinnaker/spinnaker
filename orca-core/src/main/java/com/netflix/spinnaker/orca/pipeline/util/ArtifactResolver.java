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

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

@Component
public class ArtifactResolver {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper objectMapper;
  private final ExecutionRepository executionRepository;
  private final ContextParameterProcessor contextParameterProcessor;

  @Autowired
  public ArtifactResolver(
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
    // Get all artifacts emitted by the execution's stages; we'll sort the stages topologically,
    // then reverse the result so that artifacts from later stages will appear
    // earlier in the results.
    List<Artifact> emittedArtifacts =
        Stage.topologicalSort(execution.getStages())
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
    List<Artifact> triggerArtifacts =
        objectMapper.convertValue(
            execution.getTrigger().getArtifacts(), new TypeReference<List<Artifact>>() {});

    emittedArtifacts.addAll(triggerArtifacts);

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
            boundArtifactMap, contextParameterProcessor.buildExecutionContext(stage, true), true);

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
    Execution execution =
        executionRepository.retrievePipelinesForPipelineConfigId(pipelineId, criteria)
            .subscribeOn(Schedulers.io()).toSortedList(startTimeOrId).toBlocking().single().stream()
            .findFirst()
            .orElse(null);

    return execution == null ? Collections.emptyList() : getAllArtifacts(execution);
  }

  public void resolveArtifacts(@Nonnull Map<String, Object> pipeline) {
    Map<String, Object> trigger = (Map<String, Object>) pipeline.get("trigger");
    List<ExpectedArtifact> expectedArtifacts =
        Optional.ofNullable((List<?>) pipeline.get("expectedArtifacts"))
            .map(
                list ->
                    list.stream()
                        .map(it -> objectMapper.convertValue(it, ExpectedArtifact.class))
                        .collect(toList()))
            .orElse(emptyList());

    List<Artifact> receivedArtifactsFromPipeline =
        Optional.ofNullable((List<?>) pipeline.get("receivedArtifacts"))
            .map(
                list ->
                    list.stream()
                        .map(it -> objectMapper.convertValue(it, Artifact.class))
                        .collect(toList()))
            .orElse(emptyList());
    List<Artifact> artifactsFromTrigger =
        Optional.ofNullable((List<?>) trigger.get("artifacts"))
            .map(
                list ->
                    list.stream()
                        .map(it -> objectMapper.convertValue(it, Artifact.class))
                        .collect(toList()))
            .orElse(emptyList());

    List<Artifact> receivedArtifacts =
        Stream.concat(receivedArtifactsFromPipeline.stream(), artifactsFromTrigger.stream())
            .distinct()
            .collect(toList());

    if (expectedArtifacts.isEmpty()) {
      try {
        trigger.put(
            "artifacts",
            objectMapper.readValue(objectMapper.writeValueAsString(receivedArtifacts), List.class));
      } catch (IOException e) {
        log.warn("Failure storing received artifacts: {}", e.getMessage(), e);
      }
      return;
    }

    List<Artifact> priorArtifacts = getPriorArtifacts(pipeline);
    LinkedHashSet<Artifact> resolvedArtifacts =
        resolveExpectedArtifacts(expectedArtifacts, receivedArtifacts, priorArtifacts, true);
    LinkedHashSet<Artifact> allArtifacts = new LinkedHashSet<>(receivedArtifacts);
    allArtifacts.addAll(resolvedArtifacts);

    try {
      trigger.put(
          "artifacts",
          objectMapper.readValue(objectMapper.writeValueAsString(allArtifacts), List.class));
      trigger.put(
          "expectedArtifacts",
          objectMapper.readValue(objectMapper.writeValueAsString(expectedArtifacts), List.class));
      trigger.put(
          "resolvedExpectedArtifacts",
          objectMapper.readValue(
              objectMapper.writeValueAsString(expectedArtifacts),
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

  public Artifact resolveSingleArtifact(
      ExpectedArtifact expectedArtifact,
      List<Artifact> possibleMatches,
      List<Artifact> priorArtifacts,
      boolean requireUniqueMatches) {
    Artifact resolved =
        matchSingleArtifact(expectedArtifact, possibleMatches, requireUniqueMatches);

    if (resolved == null && expectedArtifact.isUsePriorArtifact() && priorArtifacts != null) {
      resolved = matchSingleArtifact(expectedArtifact, priorArtifacts, requireUniqueMatches);
      expectedArtifact.setBoundArtifact(resolved);
    }

    if (resolved == null
        && expectedArtifact.isUseDefaultArtifact()
        && expectedArtifact.getDefaultArtifact() != null) {
      resolved = expectedArtifact.getDefaultArtifact();
      expectedArtifact.setBoundArtifact(resolved);
    }

    return resolved;
  }

  private Artifact matchSingleArtifact(
      ExpectedArtifact expectedArtifact,
      List<Artifact> possibleMatches,
      boolean requireUniqueMatches) {
    if (expectedArtifact.getBoundArtifact() != null) {
      return expectedArtifact.getBoundArtifact();
    }
    List<Artifact> matches =
        possibleMatches.stream().filter(expectedArtifact::matches).collect(toList());
    Artifact result;
    switch (matches.size()) {
      case 0:
        return null;
      case 1:
        result = matches.get(0);
        break;
      default:
        if (requireUniqueMatches) {
          throw new InvalidRequestException(
              "Expected artifact " + expectedArtifact + " matches multiple artifacts " + matches);
        }
        result = matches.get(0);
    }

    expectedArtifact.setBoundArtifact(result);
    return result;
  }

  public Set<Artifact> resolveExpectedArtifacts(
      List<ExpectedArtifact> expectedArtifacts,
      List<Artifact> receivedArtifacts,
      boolean requireUniqueMatches) {
    return resolveExpectedArtifacts(
        expectedArtifacts, receivedArtifacts, null, requireUniqueMatches);
  }

  public LinkedHashSet<Artifact> resolveExpectedArtifacts(
      List<ExpectedArtifact> expectedArtifacts,
      List<Artifact> receivedArtifacts,
      List<Artifact> priorArtifacts,
      boolean requireUniqueMatches) {
    LinkedHashSet<Artifact> resolvedArtifacts = new LinkedHashSet<>();

    for (ExpectedArtifact expectedArtifact : expectedArtifacts) {
      Artifact resolved =
          resolveSingleArtifact(
              expectedArtifact, receivedArtifacts, priorArtifacts, requireUniqueMatches);
      if (resolved == null) {
        throw new InvalidRequestException(
            format("Unmatched expected artifact %s could not be resolved.", expectedArtifact));
      } else {
        resolvedArtifacts.add(resolved);
      }
    }

    return resolvedArtifacts;
  }

  private static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Data
  public static class ResolveResult {
    Set<Artifact> resolvedArtifacts = new HashSet<>();
    Set<ExpectedArtifact> unresolvedExpectedArtifacts = new HashSet<>();
  }

  private static Func2<Execution, Execution, Integer> startTimeOrId =
      (a, b) -> {
        Long aStartTime = Optional.ofNullable(a.getStartTime()).orElse(0L);
        Long bStartTime = Optional.ofNullable(b.getStartTime()).orElse(0L);

        int startTimeCmp = bStartTime.compareTo(aStartTime);
        return startTimeCmp != 0L ? startTimeCmp : b.getId().compareTo(a.getId());
      };
}
