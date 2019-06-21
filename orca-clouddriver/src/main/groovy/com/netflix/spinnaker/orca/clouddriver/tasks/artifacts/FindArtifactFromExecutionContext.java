/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.artifacts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository.ExecutionCriteria;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;

@Getter
public class FindArtifactFromExecutionContext extends HashMap<String, Object> {
  private final ExecutionOptions executionOptions;
  private final List<ExpectedArtifact> expectedArtifacts;
  private final String pipeline;

  // There does not seem to be a way to auto-generate a constructor using our current version of
  // Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public FindArtifactFromExecutionContext(
      @JsonProperty("executionOptions") ExecutionOptions executionOptions,
      @JsonProperty("expectedArtifact") ExpectedArtifact expectedArtifact,
      @JsonProperty("expectedArtifacts") List<ExpectedArtifact> expectedArtifacts,
      @JsonProperty("pipeline") String pipeline) {
    this.executionOptions = executionOptions;
    // Previously, this stage accepted only one expected artifact
    this.expectedArtifacts =
        Optional.ofNullable(expectedArtifacts).orElse(Collections.singletonList(expectedArtifact));
    this.pipeline = pipeline;
  }

  @Data
  static class ExecutionOptions {
    // Accept either 'succeeded' or 'successful' in the stage config. The front-end sets
    // 'successful', but due to a bug
    // this class was only looking for 'succeeded'. Fix this by accepting 'successful' but to avoid
    // breaking anyone who
    // discovered this bug and manually edited their stage to set 'succeeded', continue to accept
    // 'succeeded'.
    boolean succeeded;
    boolean successful;

    boolean terminal;
    boolean running;

    ExecutionCriteria toCriteria() {
      List<String> statuses = new ArrayList<>();
      if (succeeded || successful) {
        statuses.add("SUCCEEDED");
      }

      if (terminal) {
        statuses.add("TERMINAL");
      }

      if (running) {
        statuses.add("RUNNING");
      }

      return new ExecutionCriteria().setStatuses(statuses);
    }
  }
}
