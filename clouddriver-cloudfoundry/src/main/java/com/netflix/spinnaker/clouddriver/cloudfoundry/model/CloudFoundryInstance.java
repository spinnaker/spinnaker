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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@ToString
@EqualsAndHashCode(of = {"appGuid", "key"}, callSuper = false)
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudFoundryInstance extends CloudFoundryModel implements Instance {
  private final String appGuid;

  /*
   * A sequence number that may get recycled when instances come and go.
   */
  private final String key;

  private final HealthState healthState;
  private final String details;
  private final Long launchTime;
  private final String zone;

  @Override
  public List<Map<String, Object>> getHealth() {
    Map<String, Object> health = new HashMap<>();
    health.put("healthClass", "platform");
    health.put("state", healthState.toString());
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
