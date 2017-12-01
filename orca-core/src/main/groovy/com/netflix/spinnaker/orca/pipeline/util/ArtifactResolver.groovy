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

package com.netflix.spinnaker.orca.pipeline.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.schedulers.Schedulers

@Component
class ArtifactResolver {

  @Autowired
  private ObjectMapper objectMapper

  void resolveArtifacts(ExecutionRepository repository, Map pipeline) {
    List<ExpectedArtifact> expectedArtifacts = pipeline.expectedArtifacts?.collect { objectMapper.convertValue(it, ExpectedArtifact.class) } ?: []
    List<Artifact> receivedArtifacts = pipeline.receivedArtifacts?.collect { objectMapper.convertValue(it, Artifact.class) } ?: []

    if (!expectedArtifacts) {
      return
    }

    def priorArtifacts = (List<Artifact>) repository.retrievePipelinesForPipelineConfigId((String) pipeline.get("id"), new ExecutionRepository.ExecutionCriteria())
        .subscribeOn(Schedulers.io())
        .toList()
        .toBlocking()
        .single()
        .sort(startTimeOrId)
        .getAt(0)
        ?.getTrigger()
        ?.get("artifacts")
        ?.collect { objectMapper.convertValue(it, Artifact.class) } ?: []

    ResolveResult resolve = resolveExpectedArtifacts(expectedArtifacts, receivedArtifacts)

    Set<Artifact> resolvedArtifacts = resolve.resolvedArtifacts
    Set<ExpectedArtifact> unresolvedExpectedArtifacts = resolve.unresolvedExpectedArtifacts

    for (ExpectedArtifact expectedArtifact : unresolvedExpectedArtifacts) {
      Artifact resolved = null
      if (expectedArtifact.usePriorArtifact) {
        resolved = resolveSingleArtifact(expectedArtifact, priorArtifacts);
      }

      if (!resolved && expectedArtifact.useDefaultArtifact && expectedArtifact.defaultArtifact) {
        resolved = expectedArtifact.defaultArtifact
      }

      if (!resolved) {
        throw new IllegalStateException("Unmatched expected artifact ${expectedArtifact} could not be resolved.")
      } else {
        resolvedArtifacts.add(resolved)
      }
    }

    pipeline.trigger.artifacts = resolvedArtifacts as List
    pipeline.trigger.resolvedExpectedArtifacts = expectedArtifacts // Add the actual expectedArtifacts we included in the ids.
  }

  Artifact resolveSingleArtifact(ExpectedArtifact expectedArtifact, List<Artifact> possibleMatches) {
    List<Artifact> matches = possibleMatches.findAll { a -> expectedArtifact.matches((Artifact) a) }
    switch (matches.size()) {
      case 0:
        return null
      case 1:
        return matches[0]
      default:
        throw new IllegalStateException("Expected artifact ${expectedArtifact} matches multiple artifacts ${matches}")
    }
  }

  ResolveResult resolveExpectedArtifacts(List<ExpectedArtifact> expectedArtifacts, List<Artifact> receivedArtifacts) {
    ResolveResult result = new ResolveResult()

    for (ExpectedArtifact expectedArtifact : expectedArtifacts) {
      Artifact resolved = resolveSingleArtifact(expectedArtifact, receivedArtifacts)
      if (resolved) {
        result.resolvedArtifacts.add(resolved)
      } else {
        result.unresolvedExpectedArtifacts.add(expectedArtifact)
      }
    }

    return result
  }

  static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message) {
      super(message)
    }
  }

  static class ResolveResult {
    Set<Artifact> resolvedArtifacts = new HashSet<>()
    Set<ExpectedArtifact> unresolvedExpectedArtifacts = new HashSet<>();
  }

  private static Closure startTimeOrId = { a, b ->
    def aStartTime = a.startTime ?: 0
    def bStartTime = b.startTime ?: 0

    return bStartTime <=> aStartTime ?: b.id <=> a.id
  }
}
