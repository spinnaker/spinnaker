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

package com.netflix.spinnaker.halyard.config.model.v1.metricStores.datadog;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStore;
import com.netflix.spinnaker.halyard.config.model.v1.node.MetricStores;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class DatadogStore extends MetricStore {
  @Override
  public String getNodeName() {
    return "datadog";
  }

  @JsonIgnore
  private MetricStores.MetricStoreType metricStoreType = MetricStores.MetricStoreType.DATADOG;

  @Secret(alwaysDecrypt = true)
  @JsonProperty("api_key")
  private String apiKey;

  @Secret(alwaysDecrypt = true)
  @JsonProperty("app_key")
  private String appKey;

  @JsonProperty("tags")
  private List<String> tags = new ArrayList<>();
}
