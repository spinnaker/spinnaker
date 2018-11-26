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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.functions.Func2;
import rx.schedulers.Schedulers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Component
public class ArtifactResolver {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper objectMapper;
  private final ExecutionRepository executionRepository;

  @Autowired
  public ArtifactResolver(ObjectMapper objectMapper, ExecutionRepository executionRepository) {
    this.objectMapper = objectMapper;
    this.executionRepository = executionRepository;
  }

  public @Nonnull
  List<Artifact> getArtifacts(@Nonnull Stage stage) {
    if (stage.getContext() instanceof StageContext) {
      return (List<Artifact>) Optional.ofNullable((List) ((StageContext) stage.getContext()).getAll("artifacts"))
        .map(list -> list.stream()
          .filter(Objects::nonNull)
          .flatMap(it -> ((List) it).stream())
          .map(a -> a instanceof Map ? objectMapper.convertValue(a, Artifact.class) : a)
          .collect(Collectors.toList()))
        .orElse(emptyList());
    } else {
      log.warn("Unable to read artifacts from unknown context type: {} ({})", stage.getContext().getClass(), stage.getExecution().getId());
      return emptyList();
    }
  }

  public @Nonnull
  List<Artifact> getAllArtifacts(@Nonnull Execution execution) {
    // Get all artifacts emitted by the execution's stages; we'll sort the stages topologically,
    // then reverse the result so that artifacts from later stages will appear
    // earlier in the results.
    List<Artifact> emittedArtifacts = Stage.topologicalSort(execution.getStages())
      .filter(s -> s.getOutputs().containsKey("artifacts"))
      .flatMap(
        s -> (Stream<Artifact>) ((List) s.getOutputs().get("artifacts"))
            .stream()
            .map(a -> a instanceof Map ? objectMapper.convertValue(a, Artifact.class) : a)
      ).collect(Collectors.toList());
    Collections.reverse(emittedArtifacts);

    // Get all artifacts in the parent pipeline's trigger; these artifacts go at the end of the list,
    // after any that were emitted by the pipeline
    List<Artifact> triggerArtifacts = objectMapper.convertValue(execution.getTrigger().getArtifacts(), new TypeReference<List<Artifact>>() {});

    emittedArtifacts.addAll(triggerArtifacts);

    return emittedArtifacts;
  }

  public @Nullable
  Artifact getBoundArtifactForId(
    @Nonnull Stage stage, @Nullable String id) {
    if (isEmpty(id)) {
      return null;
    }

    List<ExpectedArtifact> expectedArtifacts;
    if (stage.getContext() instanceof StageContext) {
      expectedArtifacts = (List<ExpectedArtifact>) Optional.ofNullable((List) ((StageContext) stage.getContext()).getAll("resolvedExpectedArtifacts"))
        .map(list -> list.stream()
          .filter(Objects::nonNull)
          .flatMap(it -> ((List) it).stream())
          .map(a -> a instanceof Map ? objectMapper.convertValue(a, ExpectedArtifact.class) : a)
          .collect(Collectors.toList()))
        .orElse(emptyList());
    } else {
      log.warn("Unable to read resolved expected artifacts from unknown context type: {} ({})", stage.getContext().getClass(), stage.getExecution().getId());
      expectedArtifacts = new ArrayList<>();
    }

    return expectedArtifacts
      .stream()
      .filter(e -> e.getId().equals(id))
      .findFirst()
      .map(ExpectedArtifact::getBoundArtifact)
      .orElse(null);
  }

  public @Nonnull
  List<Artifact> getArtifactsForPipelineId(
    @Nonnull String pipelineId,
    @Nonnull ExecutionCriteria criteria
  ) {
    Execution execution = executionRepository
        .retrievePipelinesForPipelineConfigId(pipelineId, criteria)
        .subscribeOn(Schedulers.io())
        .toSortedList(startTimeOrId)
        .toBlocking()
        .single()
        .stream()
        .findFirst()
        .orElse(null);

    return execution == null ? Collections.emptyList() : getAllArtifacts(execution);
  }

  public void resolveArtifacts(@Nonnull Map pipeline) {
    Map<String, Object> trigger = (Map<String, Object>) pipeline.get("trigger");
    List<ExpectedArtifact> expectedArtifacts = (List<ExpectedArtifact>) Optional.ofNullable((List) pipeline.get("expectedArtifacts"))
      .map(list -> list.stream().map(it -> objectMapper.convertValue(it, ExpectedArtifact.class)).collect(toList()))
      .orElse(emptyList());
    List<Artifact> receivedArtifactsFromPipeline = (List<Artifact>) Optional.ofNullable((List) pipeline.get("receivedArtifacts"))
      .map(list -> list.stream().map(it -> objectMapper.convertValue(it, Artifact.class)).collect(toList()))
      .orElse(emptyList());
    List<Artifact> artifactsFromTrigger = (List<Artifact>) Optional.ofNullable((List) trigger.get("artifacts"))
      .map(list -> list.stream().map(it -> objectMapper.convertValue(it, Artifact.class)).collect(toList()))
      .orElse(emptyList());
    List<Artifact> receivedArtifacts = Stream.concat(receivedArtifactsFromPipeline.stream(), artifactsFromTrigger.stream()).collect(toList());

    if (expectedArtifacts.isEmpty()) {
      try {
        trigger.put("artifacts", objectMapper.readValue(objectMapper.writeValueAsString(receivedArtifacts), List.class));
      } catch (IOException e) {
        log.warn("Failure storing received artifacts: {}", e.getMessage(), e);
      }
      return;
    }

    List<Artifact> priorArtifacts = getArtifactsForPipelineId((String) pipeline.get("id"), new ExecutionCriteria());
    Set<Artifact> resolvedArtifacts = resolveExpectedArtifacts(expectedArtifacts, receivedArtifacts, priorArtifacts, true);
    Set<Artifact> allArtifacts = new HashSet<>(receivedArtifacts);

    allArtifacts.addAll(resolvedArtifacts);

    try {
      trigger.put("artifacts", objectMapper.readValue(objectMapper.writeValueAsString(allArtifacts), List.class));
      trigger.put("resolvedExpectedArtifacts", objectMapper.readValue(objectMapper.writeValueAsString(expectedArtifacts), List.class)); // Add the actual expectedArtifacts we included in the ids.
    } catch (IOException e) {
      throw new ArtifactResolutionException("Failed to store artifacts in trigger: " + e.getMessage(), e);
    }
  }

  public Artifact resolveSingleArtifact(ExpectedArtifact expectedArtifact, List<Artifact> possibleMatches, boolean requireUniqueMatches) {
    List<Artifact> matches = possibleMatches
        .stream()
        .filter(expectedArtifact::matches)
        .collect(toList());
    Artifact result;
    switch (matches.size()) {
      case 0:
        return null;
      case 1:
        result = matches.get(0);
        break;
      default:
        if (requireUniqueMatches) {
          throw new IllegalArgumentException("Expected artifact " + expectedArtifact + " matches multiple artifacts " + matches);
        }
        result = matches.get(0);
    }

    expectedArtifact.setBoundArtifact(result);
    return result;
  }

  public Set<Artifact> resolveExpectedArtifacts(List<ExpectedArtifact> expectedArtifacts, List<Artifact> receivedArtifacts, boolean requireUniqueMatches) {
    return resolveExpectedArtifacts(expectedArtifacts, receivedArtifacts, null, requireUniqueMatches);
  }

  public Set<Artifact> resolveExpectedArtifacts(List<ExpectedArtifact> expectedArtifacts, List<Artifact> receivedArtifacts, List<Artifact> priorArtifacts, boolean requireUniqueMatches) {
    Set<Artifact> resolvedArtifacts = new HashSet<>();
    Set<ExpectedArtifact> unresolvedExpectedArtifacts = new HashSet<>();

    for (ExpectedArtifact expectedArtifact : expectedArtifacts) {
      Artifact resolved = resolveSingleArtifact(expectedArtifact, receivedArtifacts, requireUniqueMatches);
      if (resolved != null) {
        resolvedArtifacts.add(resolved);
      } else {
        unresolvedExpectedArtifacts.add(expectedArtifact);
      }
    }

    for (ExpectedArtifact expectedArtifact : unresolvedExpectedArtifacts) {
      Artifact resolved = null;
      if (expectedArtifact.isUsePriorArtifact() && priorArtifacts != null) {
        resolved = resolveSingleArtifact(expectedArtifact, priorArtifacts, requireUniqueMatches);
        expectedArtifact.setBoundArtifact(resolved);
      }

      if (resolved == null && expectedArtifact.isUseDefaultArtifact() && expectedArtifact.getDefaultArtifact() != null) {
        resolved = expectedArtifact.getDefaultArtifact();
        expectedArtifact.setBoundArtifact(resolved);
      }

      if (resolved == null) {
        throw new InvalidRequestException(format("Unmatched expected artifact %s could not be resolved.", expectedArtifact));
      } else {
        resolvedArtifacts.add(resolved);
      }
    }

    return resolvedArtifacts;
  }

  private static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message) {
      super(message);
    }

    ArtifactResolutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Data
  public static class ResolveResult {
    Set<Artifact> resolvedArtifacts = new HashSet<>();
    Set<ExpectedArtifact> unresolvedExpectedArtifacts = new HashSet<>();
  }

  private static Func2<Execution, Execution, Integer> startTimeOrId = (a, b) -> {
    Long aStartTime = Optional.ofNullable(a.getStartTime()).orElse(0L);
    Long bStartTime = Optional.ofNullable(b.getStartTime()).orElse(0L);

    int startTimeCmp = bStartTime.compareTo(aStartTime);
    return startTimeCmp != 0L ? startTimeCmp : b.getId().compareTo(a.getId());
  };
}
