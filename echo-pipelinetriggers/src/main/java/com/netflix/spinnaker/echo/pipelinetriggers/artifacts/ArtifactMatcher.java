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

import com.google.gson.Gson;
import com.jayway.jsonpath.*;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactMatcher {

  private static final Gson gson = new Gson();
  private static final Configuration conf =
      Configuration.defaultConfiguration().setOptions(Option.SUPPRESS_EXCEPTIONS);

  public static boolean anyArtifactsMatchExpected(
      List<Artifact> messageArtifacts,
      Trigger trigger,
      List<ExpectedArtifact> pipelineExpectedArtifacts) {
    messageArtifacts = messageArtifacts == null ? new ArrayList<>() : messageArtifacts;
    List<String> expectedArtifactIds = trigger.getExpectedArtifactIds();

    if (expectedArtifactIds == null || expectedArtifactIds.isEmpty()) {
      return true;
    }

    List<ExpectedArtifact> expectedArtifacts =
        pipelineExpectedArtifacts == null
            ? new ArrayList<>()
            : pipelineExpectedArtifacts.stream()
                .filter(e -> expectedArtifactIds.contains(e.getId()))
                .collect(Collectors.toList());

    if (messageArtifacts.size() > expectedArtifactIds.size()) {
      log.warn(
          "Parsed message artifacts (size {}) greater than expected artifacts (size {}), continuing trigger anyway",
          messageArtifacts.size(),
          expectedArtifactIds.size());
    }

    Predicate<Artifact> expectedArtifactMatch =
        a -> expectedArtifacts.stream().anyMatch(e -> e.matches(a));

    boolean result = messageArtifacts.stream().anyMatch(expectedArtifactMatch);
    if (!result) {
      log.info("Skipping trigger {} as artifact constraints were not satisfied", trigger);
    }
    return result;
  }

  /**
   * Check that there is a key in the payload for each constraint declared in a Trigger. Also check
   * that if there is a value for a given key, that the value matches the value in the payload.
   *
   * @param constraints A map of constraints configured in the Trigger (eg, created in Deck). A
   *     constraint is a [key, java regex value] pair.
   * @param payload A map of the payload contents POST'd in the triggering event.
   * @return Whether every key (and value if applicable) in the constraints map is represented in
   *     the payload.
   */
  public static boolean isConstraintInPayload(final Map constraints, final Map payload) {
    for (Object key : constraints.keySet()) {
      if (!payload.containsKey(key) || payload.get(key) == null) {
        return false;
      }

      if (constraints.get(key) != null
          && !matches(constraints.get(key).toString(), payload.get(key).toString())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check that there is a key in the payload for each constraint declared in a Trigger. Also check
   * that if there is a value for a given key, that the value matches the value in the payload.
   *
   * <p>The constraint key may accept a JsonPath expression for deeper json search, the evaluation
   * value should be a String or a List<String>.
   *
   * @param constraints A map of constraints configured in the Trigger (eg, created in Deck). A
   *     constraint is a [key, java regex value] pair or a [JsonPathExp, java regex value].
   * @param payload A map of the payload contents POST'd in the triggering event.
   * @return Whether every key or expression (and value if applicable) in the constraints map is
   *     represented in the payload.
   */
  public static boolean isJsonPathConstraintInPayload(final Map constraints, final Map payload) {
    String json = gson.toJson(payload);
    DocumentContext documentContext = JsonPath.using(conf).parse(json);

    for (Object key : constraints.keySet()) {
      if (!payload.containsKey(key) || payload.get(key) == null) {
        log.debug("key not present in payload, needs to check with jsonpath");
        List<String> values = getValueUsingJsonPath(documentContext, key.toString());
        if (values != null && anyMatch(constraints.get(key).toString(), values)) {
          continue;
        }
        return false;
      } else {
        if (constraints.get(key) != null
            && !matches(constraints.get(key).toString(), payload.get(key).toString())) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean matches(String us, String other) {
    Pattern p;
    try {
      p = Pattern.compile(us);
    } catch (PatternSyntaxException ex) {
      log.error(
          "Invalid regex pattern for constraint, will never match any payload: \""
              + us
              + "\": "
              + ex.getMessage(),
          ex);
      return false;
    }
    return p.asPredicate().test(other);
  }

  private static boolean anyMatch(String us, List<String> values) {
    return values.stream().anyMatch(v -> matches(us, v));
  }

  private static List<String> getValueUsingJsonPath(DocumentContext ctx, String query) {
    try {
      String value = ctx.read(query, String.class);
      if (value != null) {
        return Collections.singletonList(value);
      }
      // lets try to cast to List<String>
      return ctx.read(query, List.class);
    } catch (InvalidPathException e) {
      log.error("Invalid JsonPath query in constrains, with query: " + query);
    } catch (ClassCastException e) {
      log.error(
          "Payload value cannot be cast, only String or List<String> are valid types, with query: "
              + query);
    }
    return null;
  }
}
