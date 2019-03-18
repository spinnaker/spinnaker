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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;

@Getter
public class DeployManifestContext extends HashMap<String, Object> {
  private final String source;
  private final String manifestArtifactId;
  private final String manifestArtifactAccount;
  private final Boolean skipExpressionEvaluation;
  private final List<String> requiredArtifactIds;

  // There does not seem to be a way to auto-generate a constructor using our current version of Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public DeployManifestContext(
    @JsonProperty("source") String source,
    @JsonProperty("manifestArtifactId") String manifestArtifactId,
    @JsonProperty("manifestArtifactAccount") String manifestArtifactAccount,
    @JsonProperty("skipExpressionEvaluation") Boolean skipExpressionEvaluation,
    @JsonProperty("requiredArtifactIds") List<String> requiredArtifactIds
  ){
    this.source = source;
    this.manifestArtifactId = manifestArtifactId;
    this.manifestArtifactAccount = manifestArtifactAccount;
    this.skipExpressionEvaluation = skipExpressionEvaluation;
    this.requiredArtifactIds = requiredArtifactIds;
  }
}
