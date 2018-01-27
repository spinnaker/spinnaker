/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

@JsonTypeName("travis")
public class TravisTrigger extends JenkinsTrigger {
  @JsonCreator
  public TravisTrigger(
    @JsonProperty("master") @Nonnull String master,
    @JsonProperty("job") @Nonnull String job,
    @JsonProperty("buildNumber") int buildNumber,
    @JsonProperty("propertyFile") @Nullable String propertyFile,
    @JsonProperty("properties") @Nonnull Map<String, Object> properties,
    @JsonProperty("buildInfo") @Nonnull BuildInfo buildInfo,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters,
    @JsonProperty("artifacts") @Nullable List<Artifact> artifacts
  ) {
    super(master, job, buildNumber, propertyFile, properties, buildInfo, user, parameters, artifacts);
  }
}
