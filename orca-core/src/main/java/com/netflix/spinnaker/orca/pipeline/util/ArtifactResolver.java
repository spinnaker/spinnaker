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

import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
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
          .collect(Collectors.toList()))
        .orElse(emptyList());
    } else {
      log.warn("Unable to read artifacts from unknown context type: {} ({})", stage.getContext().getClass(), stage.getExecution().getId());
      return emptyList();
    }
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
    return executionRepository
      .retrievePipelinesForPipelineConfigId(pipelineId, criteria)
      .subscribeOn(Schedulers.io())
      .toSortedList(startTimeOrId)
      .toBlocking()
      .single()
      .stream()
      .map(Execution::getTrigger)
      .map(Trigger::getArtifacts)
      .findFirst()
      .orElse(emptyList());
  }

  public void resolveArtifacts(@Nonnull Map pipeline) {
    List<ExpectedArtifact> expectedArtifacts = (List<ExpectedArtifact>) Optional.ofNullable((List) pipeline.get("expectedArtifacts"))
      .map(list -> list.stream().map(it -> objectMapper.convertValue(it, ExpectedArtifact.class)).collect(toList()))
      .orElse(emptyList());
    List<Artifact> receivedArtifacts = (List<Artifact>) Optional.ofNullable((List) pipeline.get("receivedArtifacts"))
      .map(list -> list.stream().map(it -> objectMapper.convertValue(it, Artifact.class)).collect(toList()))
      .orElse(emptyList());

    if (expectedArtifacts.isEmpty()) {
      return;
    }

    List<Artifact> priorArtifacts = getArtifactsForPipelineId((String) pipeline.get("id"), new ExecutionCriteria());
    ResolveResult resolve = resolveExpectedArtifacts(expectedArtifacts, receivedArtifacts);

    Set<Artifact> resolvedArtifacts = resolve.resolvedArtifacts;
    Set<ExpectedArtifact> unresolvedExpectedArtifacts = resolve.unresolvedExpectedArtifacts;

    for (ExpectedArtifact expectedArtifact : unresolvedExpectedArtifacts) {
      Artifact resolved = null;
      if (expectedArtifact.isUsePriorArtifact()) {
        resolved = resolveSingleArtifact(expectedArtifact, priorArtifacts);
      }

      if (resolved == null && expectedArtifact.isUseDefaultArtifact() && expectedArtifact.getDefaultArtifact() != null) {
        resolved = expectedArtifact.getDefaultArtifact();
      }

      if (resolved == null) {
        throw new IllegalStateException(format("Unmatched expected artifact %s could not be resolved.", expectedArtifact));
      } else {
        expectedArtifact.setBoundArtifact(resolved);
        resolvedArtifacts.add(resolved);
      }
    }

    Map<String, Object> trigger = (Map<String, Object>) pipeline.get("trigger");
    trigger.put("artifacts", resolvedArtifacts);
    trigger.put("resolvedExpectedArtifacts", expectedArtifacts);// Add the actual expectedArtifacts we included in the ids.
  }

  public Artifact resolveSingleArtifact(ExpectedArtifact expectedArtifact, List<Artifact> possibleMatches) {
    List<Artifact> matches = possibleMatches
      .stream()
      .filter(expectedArtifact::matches)
      .collect(toList());
    switch (matches.size()) {
      case 0:
        return null;
      case 1:
        return matches.get(0);
      default:
        throw new IllegalStateException(format("Expected artifact %s matches multiple artifacts %s", expectedArtifact, matches));
    }
  }

  private ResolveResult resolveExpectedArtifacts(List<ExpectedArtifact> expectedArtifacts, List<Artifact> receivedArtifacts) {
    ResolveResult result = new ResolveResult();

    for (ExpectedArtifact expectedArtifact : expectedArtifacts) {
      Artifact resolved = resolveSingleArtifact(expectedArtifact, receivedArtifacts);
      if (resolved != null) {
        expectedArtifact.setBoundArtifact(resolved);
        result.resolvedArtifacts.add(resolved);
      } else {
        result.unresolvedExpectedArtifacts.add(expectedArtifact);
      }
    }

    return result;
  }

  private static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message) {
      super(message);
    }
  }

  private static class ResolveResult {
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
