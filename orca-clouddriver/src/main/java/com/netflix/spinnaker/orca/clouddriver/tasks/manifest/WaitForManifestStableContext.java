/*
 * Copyright 2019 Cerner Corporation
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public class WaitForManifestStableContext extends HashMap<String, Object> {

  private final List<String> messages;
  private final List<String> failureMessages;
  private final List<Map<String, String>> stableManifests;
  private final List<Map<String, String>> failedManifests;
  private final List warnings;

  // There does not seem to be a way to auto-generate a constructor using our current version of
  // Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public WaitForManifestStableContext(
      @JsonProperty("messages") Optional<List<String>> messages,
      @JsonProperty("exception") Optional<Exception> exception,
      @JsonProperty("stableManifests") Optional<List<Map<String, String>>> stableManifests,
      @JsonProperty("failedManifests") Optional<List<Map<String, String>>> failedManifests,
      @JsonProperty("warnings") Optional<List> warnings) {
    this.messages = messages.orElseGet(ArrayList::new);
    this.failureMessages =
        exception
            .flatMap((e) -> e.getDetails().map(details -> details.get("errors")))
            .orElseGet(ArrayList::new);
    this.stableManifests = stableManifests.orElseGet(ArrayList::new);
    this.failedManifests = failedManifests.orElseGet(ArrayList::new);
    this.warnings = warnings.orElseGet(ArrayList::new);
  }

  public List<Map<String, String>> getCompletedManifests() {
    return Stream.concat(stableManifests.stream(), failedManifests.stream())
        .collect(Collectors.toList());
  }

  @Getter
  private static class Exception {
    private final Optional<Map<String, List<String>>> details;

    public Exception(@JsonProperty("details") Optional<Map<String, List<String>>> details) {
      this.details = details;
    }
  }
}
