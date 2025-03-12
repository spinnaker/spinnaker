/*
 * Copyright 2022 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.cloudrun.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.run.v1.model.Revision;
import com.google.api.services.run.v1.model.Service;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import java.io.Serializable;
import java.util.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CloudrunInstance implements Instance, Serializable {
  private String name;
  private String id;
  private Long launchTime;
  private CloudrunInstanceStatus instanceStatus;
  private String zone;
  private String serverGroup;
  private Collection<String> loadBalancers;
  private final String providerType = CloudrunCloudProvider.ID;
  private final String cloudProvider = CloudrunCloudProvider.ID;
  private List<Map<String, Object>> health;

  public CloudrunInstance(Revision revision, Service service, String region) {
    Map<String, Object> map = new CloudrunHealth(revision, service).toMap();
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(map);
    this.health = list;
    this.instanceStatus = CloudrunInstanceStatus.DYNAMIC;
    this.zone = region;
    this.name = revision.getMetadata().getName() + "-instance";
    this.id = this.name;
    this.launchTime =
        CloudrunModelUtil.translateTime(revision.getMetadata().getCreationTimestamp());
    this.serverGroup = revision.getMetadata().getName();
    this.loadBalancers = Set.of(service.getMetadata().getName());
  }

  public HealthState getHealthState() {
    return new ObjectMapper().convertValue(this.health.get(0).get("state"), HealthState.class);
  }

  public static enum CloudrunInstanceStatus {
    DYNAMIC,
    UNKNOWN;
  }
}
