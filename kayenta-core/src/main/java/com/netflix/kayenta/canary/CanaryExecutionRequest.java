/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.kayenta.canary;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class CanaryExecutionRequest {
  @NotNull
  protected Map<String, CanaryScopePair> scopes;

  protected CanaryClassifierThresholdsConfig thresholds;

  public Duration calculateDuration() {
    Set<Duration> durationsFound = new HashSet<>();

    if (scopes != null) {
      scopes.values().forEach(scope -> {
        durationsFound.add(scope.controlScope.calculateDuration());
        durationsFound.add(scope.experimentScope.calculateDuration());
      });
    }
    if (durationsFound.size() == 1) {
      return durationsFound.stream()
        .findFirst()
        .orElse(null);
    }
    return null;  // cannot find a single duration to represent this data
  }
}
