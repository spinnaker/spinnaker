/*
 * Copyright 2018 Pivotal, Inc.
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

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value
@EqualsAndHashCode(of = {"appGuid", "key"}, callSuper = false)
@Builder
@JsonDeserialize(builder = CloudFoundryInstance.CloudFoundryInstanceBuilder.class)
public class CloudFoundryInstance extends CloudFoundryModel implements Instance {
  @JsonView(Views.Cache.class)
  String appGuid;

  /*
   * A sequence number that may get recycled when instances come and go.
   */
  @JsonView(Views.Cache.class)
  String key;

  @JsonView(Views.Cache.class)
  HealthState healthState;

  @JsonView(Views.Cache.class)
  String details;

  @JsonView(Views.Cache.class)
  Long launchTime;

  @JsonView(Views.Cache.class)
  String zone;

  @Override
  public List<Map<String, Object>> getHealth() {
    Map<String, Object> health = new HashMap<>();
    health.put("healthClass", "platform");
    health.put("state", (healthState == null ? HealthState.Unknown : healthState).toString());
    return Collections.singletonList(health);
  }

  @Deprecated
  public String getProviderType() {
    return CloudFoundryCloudProvider.ID;
  }

  public String getId() {
    return appGuid + "-" + key;
  }

  public String getName() {
    return appGuid + "-" + key;
  }
}
