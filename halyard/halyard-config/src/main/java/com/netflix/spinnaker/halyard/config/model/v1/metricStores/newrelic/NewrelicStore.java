/*
 * Copyright 2019 New Relic Corporation. All rights reserved.
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

package com.netflix.spinnaker.halyard.config.model.v1.metricStores.newrelic;

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
public class NewrelicStore extends MetricStore {
  @Override
  public String getNodeName() {
    return "newrelic";
  }

  @JsonIgnore
  private MetricStores.MetricStoreType metricStoreType = MetricStores.MetricStoreType.NEWRELIC;

  @Secret(alwaysDecrypt = true)
  @JsonProperty("insert_key")
  private String insertKey;

  @JsonProperty("host")
  private String host;

  @JsonProperty("tags")
  private List<String> tags = new ArrayList<>();
}
