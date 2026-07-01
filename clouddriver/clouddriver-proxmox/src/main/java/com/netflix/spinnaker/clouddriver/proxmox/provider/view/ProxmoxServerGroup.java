/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.proxmox.provider.view;

import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.proxmox.ProxmoxProvider;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ProxmoxServerGroup implements ServerGroup {
  private String name;
  private String application;
  private String cloudProvider = ProxmoxProvider.ID;
  private String region;
  private Boolean disabled;
  private Long createdTime;
  private Set<String> zones;
  private Set<Instance> instances;
  private Set<String> loadBalancers;
  private Set<String> securityGroups;
  private Map<String, Object> launchConfig;
  private InstanceCounts instanceCounts;
  private Capacity capacity;
  private Moniker moniker;

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  @Override
  public Moniker getMoniker() {
    return moniker;
  }

  @Override
  public ImagesSummary getImagesSummary() {
    return null;
  }

  @Override
  public ImageSummary getImageSummary() {
    return null;
  }
}
