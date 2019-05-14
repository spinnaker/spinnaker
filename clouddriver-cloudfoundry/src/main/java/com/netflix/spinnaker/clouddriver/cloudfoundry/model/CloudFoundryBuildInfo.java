/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.Value;

/** The CI build metadata for an app artifact based on the build info produced by Artifactory */
@Value
@Builder
@JsonDeserialize(builder = CloudFoundryBuildInfo.CloudFoundryBuildInfoBuilder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CloudFoundryBuildInfo {

  @JsonView(Views.Cache.class)
  String jobName;

  @JsonView(Views.Cache.class)
  String jobNumber;

  @JsonView(Views.Cache.class)
  String jobUrl;

  @JsonView(Views.Cache.class)
  String version;
}
