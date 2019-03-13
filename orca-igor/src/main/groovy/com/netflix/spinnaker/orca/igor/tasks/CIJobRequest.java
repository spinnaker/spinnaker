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

package com.netflix.spinnaker.orca.igor.tasks;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Optional;

@Getter
public class CIJobRequest {
  private final String master;
  private final String job;
  private final String propertyFile;
  private final Integer buildNumber;
  private final int consecutiveErrors;

  // There does not seem to be a way to auto-generate a constructor using our current version of Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public CIJobRequest(
    @JsonProperty("master") String master,
    @JsonProperty("job") String job,
    @JsonProperty("property") String propertyFile,
    @JsonProperty("buildNumber") Integer buildNumber,
    @JsonProperty("consecutiveErrors") Integer consecutiveErrors
  ) {
    this.master = master;
    this.job = job;
    this.propertyFile = propertyFile;
    this.buildNumber = buildNumber;
    this.consecutiveErrors = Optional.ofNullable(consecutiveErrors).orElse(0);
  }
}
