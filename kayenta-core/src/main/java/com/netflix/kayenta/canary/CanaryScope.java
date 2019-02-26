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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CanaryScope {

  @NotNull
  protected String scope;

  @NotNull
  private String location;

  @NotNull
  protected Instant start;

  @NotNull
  protected Instant end;

  @NotNull
  protected Long step;

  // Metric source specific parameters which may be used to further
  // alter the canary scope.
  Map<String, String> extendedScopeParams;

  public Duration calculateDuration() {
    return Duration.between(start, end);
  }
}
