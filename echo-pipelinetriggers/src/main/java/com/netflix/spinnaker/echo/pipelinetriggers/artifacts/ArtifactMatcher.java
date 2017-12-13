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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class ArtifactMatcher {
  public static Boolean anyArtifactsMatchExpected(List<Artifact> messageArtifacts, Trigger trigger, Pipeline pipeline) {
    messageArtifacts = messageArtifacts == null ? new ArrayList<>() : messageArtifacts;
    List<String> expectedArtifactIds = trigger.getExpectedArtifactIds();

    if (expectedArtifactIds == null || expectedArtifactIds.isEmpty()) {
      return true;
    }

    List<ExpectedArtifact> pipelineExpectedArtifacts = pipeline.getExpectedArtifacts();
    List<ExpectedArtifact> expectedArtifacts = pipelineExpectedArtifacts == null ? new ArrayList<>() : pipelineExpectedArtifacts
        .stream()
        .filter(e -> expectedArtifactIds.contains(e.getId()))
        .collect(Collectors.toList());

    if (messageArtifacts.size() > expectedArtifactIds.size()) {
      log.warn("Parsed message artifacts (size {}) greater than expected artifacts (size {}), continuing trigger anyway", messageArtifacts.size(), expectedArtifactIds.size());
    }

    Predicate<Artifact> expectedArtifactMatch = a -> expectedArtifacts
        .stream()
        .anyMatch(e -> e.matches(a));
    return messageArtifacts.stream().anyMatch(expectedArtifactMatch);
  }

  /**
   * Check that there is a key in the payload for each constraint declared in a Trigger.
   * Also check that if there is a value for a given key, that the value matches the value in the payload.
   * @param constraints A map of constraints configured in the Trigger (eg, created in Deck).
   * @param payload A map of the payload contents POST'd in the Webhook.
   * @return Whether every key (and value if applicable) in the constraints map is represented in the payload.
   */
  public static boolean isConstraintInPayload(final Map constraints, final Map payload) {
    for (Object key : constraints.keySet()) {
      if (!payload.containsKey(key) || payload.get(key) == null) {
        log.info("Trigger ignored. Item " + key.toString() + " was not found in payload");
        return false;
      }

      if (constraints.get(key) != null && !matches(constraints.get(key).toString(), payload.get(key).toString()) ) {
        log.info("Trigger ignored. Value of item " + key.toString() + " (" + payload.get(key) + ") in payload does not match constraint " + constraints.get(key));
        return false;
      }
    }
    return true;
  }

  private static boolean matches(String us, String other) {
    return Pattern.compile(us).asPredicate().test(other);
  }
}
