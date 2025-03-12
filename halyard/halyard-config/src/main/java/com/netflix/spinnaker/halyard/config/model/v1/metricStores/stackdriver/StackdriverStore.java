/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.metricStores.stackdriver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.halyard.config.model.v1.node.LocalFile;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import com.netflix.spinnaker.halyard.config.model.v1.node.SecretFile;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class StackdriverStore extends MetricStore {
  @Override
  public String getNodeName() {
    return "stackdriver";
  }

  @JsonIgnore
  private MetricStores.MetricStoreType metricStoreType = MetricStores.MetricStoreType.STACKDRIVER;

  @JsonProperty("credentials_path")
  @LocalFile
  @SecretFile(alwaysDecrypt = true)
  private String credentialsPath;

  private String project;

  private String zone;

  @JsonProperty("instance_id")
  private String instanceId;
}
