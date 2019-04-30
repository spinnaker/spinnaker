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

package com.netflix.spinnaker.orca.igor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

@Getter
public class GoogleCloudBuildStageDefinition implements RetryableStageDefinition {
  private final String account;
  private final GoogleCloudBuild buildInfo;
  private final Map<String, Object> buildDefinition;
  private final int consecutiveErrors;

  // There does not seem to be a way to auto-generate a constructor using our current version of Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public GoogleCloudBuildStageDefinition(
    @JsonProperty("account") String account,
    @JsonProperty("buildInfo") GoogleCloudBuild build,
    @JsonProperty("buildDefinition") Map<String, Object> buildDefinition,
    @JsonProperty("consecutiveErrors") Integer consecutiveErrors
  ) {
    this.account = account;
    this.buildInfo = build;
    this.buildDefinition = buildDefinition;
    this.consecutiveErrors = Optional.ofNullable(consecutiveErrors).orElse(0);
  }
}
