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

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact
import groovy.util.logging.Slf4j

import java.lang.reflect.Field

@Slf4j
class ArtifactResolver {

  static void resolveArtifacts(Map pipeline) {
    Set<Artifact> resolvedArtifacts = []
    List<Artifact> receivedArtifacts = pipeline.receivedArtifacts ?: []
    List<ExpectedArtifact> expectedArtifacts = pipeline.trigger.expectedArtifacts ?: []
    List<ExpectedArtifact> unresolvedExpectedArtifacts = []

    for (ExpectedArtifact expectedArtifact : expectedArtifacts) {
      List<Artifact> matches = receivedArtifacts.findAll { a -> expectedArtifact.matches((Artifact) a) }
      switch (matches.size()) {
        case 0:
          unresolvedExpectedArtifacts.add(expectedArtifact)
          continue
        case 1:
          resolvedArtifacts.add(matches[0])
          continue
        default:
          throw new IllegalStateException("Expected artifact ${expectedArtifact} matches multiple incoming artifacts ${matches}")
      }
    }

    for (ExpectedArtifact expectedArtifact : unresolvedExpectedArtifacts) {
      if (expectedArtifact.usePriorArtifact) {
        throw new UnsupportedOperationException("'usePriorArtifact' is not supported yet")
      } else if (expectedArtifact.useDefaultArtifact && expectedArtifact.defaultArtifact) {
        resolvedArtifacts.add(expectedArtifact.defaultArtifact)
      } else {
        throw new IllegalStateException("Unmatched expected artifact ${expectedArtifact} with no fallback behavior specified")
      }
    }

    pipeline.trigger.artifacts = resolvedArtifacts as List
  }

  static class ArtifactResolutionException extends RuntimeException {
    ArtifactResolutionException(String message) {
      super(message)
    }

  }
}
