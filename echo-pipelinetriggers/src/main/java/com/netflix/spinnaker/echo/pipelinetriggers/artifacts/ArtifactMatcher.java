/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers.artifacts;

import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
public class ArtifactMatcher {
  public static Boolean anyArtifactsMatchExpected(List<Artifact> messageArtifacts, Trigger trigger, Pipeline pipeline) {
    List<String> expectedArtifactIds = trigger.getExpectedArtifactIds();
    List<ExpectedArtifact> expectedArtifacts = pipeline.getExpectedArtifacts()
        .stream()
        .filter(e -> expectedArtifactIds.contains(e.getId()))
        .collect(Collectors.toList());

    if (expectedArtifactIds == null || expectedArtifactIds.isEmpty()) {
      return true;
    }

    if (messageArtifacts.size() > expectedArtifactIds.size()) {
      log.warn("Parsed message artifacts (size {}) greater than expected artifacts (size {}), continuing trigger anyway", messageArtifacts.size(), expectedArtifactIds.size());
    }

    Predicate<Artifact> expectedArtifactMatch = a -> expectedArtifacts
        .stream()
        .anyMatch(e -> e.matches(a));
    return messageArtifacts.stream().anyMatch(expectedArtifactMatch);
  }
}
