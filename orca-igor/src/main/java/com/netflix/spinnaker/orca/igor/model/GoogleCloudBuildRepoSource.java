/*
 * Copyright 2019 Andres Castano.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleCloudBuildRepoSource {
  private final String branchName;
  private final String commitSha;
  private final String tagName;

  @JsonCreator
  public GoogleCloudBuildRepoSource(
      @JsonProperty("branchName") String branchName,
      @JsonProperty("commitSha") String commitSha,
      @JsonProperty("tagName") String tagName) {
    this.branchName = branchName;
    this.commitSha = commitSha;
    this.tagName = tagName;
  }
}
